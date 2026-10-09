package com.ruoyi.system.finance;

import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

/** Private evidence is bound to its uploader and business, never served by /profile. */
@Component
public class FinancePrivateStorage {
  private final String configuredRoot;
  private final Clock clock;

  @Autowired
  public FinancePrivateStorage(
      @Value("${finance.private-root:${RUOYI_FINANCE_PRIVATE_PATH:}}") String root) {
    this(root, Clock.systemDefaultZone());
  }

  FinancePrivateStorage(String root, Clock clock) {
    this.configuredRoot = root;
    this.clock = clock;
  }

  private Path root() throws IOException {
    String profile = RuoYiConfig.getProfile();
    if (profile == null || profile.isBlank()) throw new IOException("missing profile");
    Path publicRoot = Path.of(profile).toFile().getCanonicalFile().toPath();
    Path privateRoot =
        Path.of(configuredRoot.isBlank() ? profile + "-finance-private" : configuredRoot)
            .toFile()
            .getCanonicalFile()
            .toPath();
    if (privateRoot.startsWith(publicRoot) || publicRoot.startsWith(privateRoot))
      throw new IOException("unsafe private root");
    Files.createDirectories(privateRoot);
    return privateRoot.toRealPath();
  }

  private Path safe(String key) throws IOException {
    if (key == null || !key.matches("(?:evidence|receipts)/[a-f0-9]{32}\\.(?:pdf|png|jpg)"))
      throw new IOException("bad key");
    Path base = root();
    Path path = base.resolve(key);
    Files.createDirectories(path.getParent());
    if (!path.getParent().toRealPath().startsWith(base)) throw new IOException("symlink");
    if (Files.exists(path)
        && (!path.toRealPath().startsWith(base)
            || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
      throw new IOException("bad file");
    return path;
  }

  public Map<String, Object> uploadEvidence(long actor, String type, long id, MultipartFile file)
      throws IOException {
    if (!Set.of("PAYMENT", "REFUND", "HISTORY").contains(type) || id <= 0 || actor <= 0)
      throw new ServiceException("凭证业务参数无效");
    String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
    if (file.isEmpty() || file.getSize() > 10L * 1024 * 1024)
      throw new ServiceException("请上传10MB以内的PDF、PNG或JPEG凭证");
    byte[] bytes;
    try (var in = file.getInputStream()) {
      bytes = in.readNBytes(10 * 1024 * 1024 + 1);
    }
    if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
      throw new ServiceException("凭证大小超出限制");
    String ext =
        name.endsWith(".pdf")
            ? "pdf"
            : name.endsWith(".png")
                ? "png"
                : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "jpg" : "";
    try {
      if (ext.equals("pdf")) {
        if (bytes.length < 5
            || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
          throw new IOException();
        try (var doc = Loader.loadPDF(bytes)) {
          if (doc.isEncrypted() || doc.getNumberOfPages() == 0 || doc.getNumberOfPages() > 100)
            throw new IOException();
        }
      } else if (ext.equals("png") || ext.equals("jpg")) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
          var readers = ImageIO.getImageReaders(input);
          if (!readers.hasNext()) throw new IOException();
          var reader = readers.next();
          try {
            reader.setInput(input);
            String format = reader.getFormatName();
            if (!(ext.equals("png") && format.equalsIgnoreCase("png")
                || ext.equals("jpg") && format.equalsIgnoreCase("JPEG"))) throw new IOException();
            if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000L
                || reader.read(0) == null) throw new IOException();
          } finally {
            reader.dispose();
          }
        }
      } else throw new IOException();
    } catch (Exception e) {
      throw new ServiceException("凭证内容或类型无效，请使用完整的PDF、PNG或JPEG");
    }
    String key = "evidence/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
    try {
      Path path = safe(key);
      Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
      Properties p = new Properties();
      p.setProperty("actor", Long.toString(actor));
      p.setProperty("type", type);
      p.setProperty("business", Long.toString(id));
      p.setProperty("created", Long.toString(clock.millis()));
      p.setProperty("bound", "false");
      writeMetadata(path, p);
    } catch (IOException e) {
      throw new ServiceException("私有凭证目录不可用，请检查配置");
    }
    return Map.of("evidenceKey", key, "fileSize", Integer.toString(bytes.length));
  }

  public String validateEvidence(String key, long actor, String type, long id) {
    try {
      Path path = safe(key);
      Properties p = metadata(path);
      if (!p.getProperty("actor", "").equals(Long.toString(actor))
          || !p.getProperty("type", "").equals(type)
          || !p.getProperty("business", "").equals(Long.toString(id))
          || !Files.isRegularFile(path)) throw new IOException();
      if (!Boolean.parseBoolean(p.getProperty("bound"))
          && clock.millis() - Long.parseLong(p.getProperty("created")) > 86400000L)
        throw new IOException();
      Runnable bind =
          () -> {
            try {
              p.setProperty("bound", "true");
              writeMetadata(path, p);
            } catch (IOException e) {
              org.slf4j.LoggerFactory.getLogger(getClass()).error("财务凭证绑定元数据写入失败，请检查私有目录", e);
            }
          };
      if (TransactionSynchronizationManager.isSynchronizationActive())
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCommit() {
                bind.run();
              }
            });
      else bind.run();
      return key;
    } catch (Exception e) {
      throw new ServiceException("凭证不存在、已过期或不属于当前操作及业务，请重新上传");
    }
  }

  private Properties metadata(Path path) throws IOException {
    Path meta = path.resolveSibling(path.getFileName() + ".meta");
    if (Files.isSymbolicLink(meta)) throw new IOException();
    Properties p = new Properties();
    try (var in = Files.newInputStream(meta)) {
      p.load(in);
    }
    return p;
  }

  private void writeMetadata(Path path, Properties p) throws IOException {
    Path tmp = Files.createTempFile(path.getParent(), ".evidence-", ".tmp");
    try {
      try (var out = Files.newOutputStream(tmp)) {
        p.store(out, "private finance evidence binding");
      }
      Files.move(
          tmp,
          path.resolveSibling(path.getFileName() + ".meta"),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  public Path resolveEvidence(String key) {
    if (key == null || !key.startsWith("evidence/")) throw new ServiceException("凭证路径无效");
    return resolve(key);
  }

  public Path resolve(String key) {
    try {
      Path path = safe(key);
      if (!Files.isRegularFile(path)) throw new IOException();
      return path;
    } catch (IOException e) {
      throw new ServiceException("私有财务文件不存在或不可用");
    }
  }

  public String publishReceipt(byte[] bytes) throws IOException {
    String key = "receipts/" + UUID.randomUUID().toString().replace("-", "") + ".pdf";
    Path target = safe(key);
    Path tmp = Files.createTempFile(target.getParent(), ".receipt-", ".tmp");
    try {
      Files.write(tmp, bytes);
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(tmp);
    }
    return key;
  }

  public void discardReceipt(String key) {
    try {
      if (key != null && key.startsWith("receipts/")) Files.deleteIfExists(safe(key));
    } catch (IOException ignored) {
    }
  }
}

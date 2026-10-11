package com.ruoyi.system.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

class FinanceFilesTest {
  @TempDir Path temp;
  FinancePrivateStorage storage;

  @BeforeEach
  void init() {
    new RuoYiConfig().setProfile(temp.resolve("public").toString());
    storage = new FinancePrivateStorage(temp.resolve("private").toString());
  }

  @Test
  void privateEvidenceChecksUploaderBusinessExpiryAndContent() throws Exception {
    byte[] png = png();
    var upload = storage.uploadEvidence(7, "PAYMENT", 19, new FilePart("voucher.png", png));
    String key = upload.get("evidenceKey").toString();
    assertThrows(ServiceException.class, () -> storage.validateEvidence(key, 8, "PAYMENT", 19));
    assertThrows(ServiceException.class, () -> storage.validateEvidence(key, 7, "REFUND", 19));
    assertThrows(ServiceException.class, () -> storage.validateEvidence(key, 7, "PAYMENT", 20));
    assertEquals(key, storage.validateEvidence(key, 7, "PAYMENT", 19));
    assertArrayEquals(png, Files.readAllBytes(storage.resolveEvidence(key)));
    assertThrows(
        ServiceException.class,
        () -> storage.uploadEvidence(7, "PAYMENT", 19, new FilePart("fake.pdf", png)));
    assertThrows(
        ServiceException.class,
        () ->
            storage.uploadEvidence(
                7, "PAYMENT", 19, new FilePart("fake.png", "not a PNG".getBytes())));
    assertThrows(ServiceException.class, () -> storage.resolveEvidence("../../public/password"));
  }

  @Test
  void unboundUploadsExpireButBoundFilesSurviveAndStayPrivate() throws Exception {
    var old =
        new FinancePrivateStorage(
            temp.resolve("private").toString(),
            Clock.fixed(Instant.now().minusSeconds(90000), ZoneId.systemDefault()));
    String unbound =
        old.uploadEvidence(7, "HISTORY", 19, new FilePart("voucher.png", png()))
            .get("evidenceKey")
            .toString();
    assertThrows(ServiceException.class, () -> storage.validateEvidence(unbound, 7, "HISTORY", 19));
    String bound =
        old.uploadEvidence(7, "REFUND", 19, new FilePart("voucher.png", png()))
            .get("evidenceKey")
            .toString();
    old.validateEvidence(bound, 7, "REFUND", 19);
    assertEquals(bound, storage.validateEvidence(bound, 7, "REFUND", 19));
    assertTrue(Files.isRegularFile(storage.resolveEvidence(bound)));
    assertThrows(
        ServiceException.class,
        () ->
            new FinancePrivateStorage(temp.resolve("public/nested").toString())
                .uploadEvidence(7, "PAYMENT", 19, new FilePart("voucher.png", png())));
  }

  @Test
  void receiptEmbedsChineseAndWrapsLongSnapshotsWithoutChangingAmounts() throws Exception {
    var snapshot = new LinkedHashMap<String, Object>();
    snapshot.put("institutionTitle", "名远教育");
    snapshot.put("receiptNo", "RC202610070001");
    snapshot.put("studentName", "张同学");
    snapshot.put("courseClassName", "五年级数学提高班".repeat(120));
    snapshot.put("amount", "900.00");
    snapshot.put("originalAmount", "1000.00");
    snapshot.put("discountAmount", "100.00");
    snapshot.put("financeMode", "MOCK");
    snapshot.put("payerName", "张家长");
    snapshot.put("channel", "MOCK");
    snapshot.put("paidTime", "2026-10-07 12:00:00");
    snapshot.put("documentType", "RECEIPT");
    byte[] pdf = FinanceReceiptPdf.render(snapshot);
    try (var doc = Loader.loadPDF(pdf)) {
      String text = new PDFTextStripper().getText(doc);
      assertTrue(text.contains("名远教育"));
      assertTrue(text.contains("张同学"));
      assertTrue(text.contains("900.00"));
      assertTrue(text.contains("模拟"));
      assertTrue(doc.getNumberOfPages() > 1);
      for (var page : doc.getPages())
        for (var name : page.getResources().getFontNames())
          assertTrue(page.getResources().getFont(name).isEmbedded());
      doc.save(temp.resolve("receipt-preview.pdf").toFile());
      if (System.getProperty("finance.pdf.preview") != null)
        doc.save(System.getProperty("finance.pdf.preview"));
    }
  }

  static byte[] png() throws IOException {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  record FilePart(String name, byte[] content) implements MultipartFile {
    public String getName() {
      return "file";
    }

    public String getOriginalFilename() {
      return name;
    }

    public String getContentType() {
      return "application/octet-stream";
    }

    public boolean isEmpty() {
      return content.length == 0;
    }

    public long getSize() {
      return content.length;
    }

    public byte[] getBytes() {
      return content;
    }

    public InputStream getInputStream() {
      return new ByteArrayInputStream(content);
    }

    public void transferTo(File file) throws IOException {
      Files.write(file.toPath(), content);
    }
  }
}

package com.ruoyi.system.shop;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;

@Component
public class PrivateStorage {
    private final Path configuredRoot;
    public PrivateStorage(@Value("${material.shop.private-root:}") String root) {
        configuredRoot=root.isBlank()?null:Path.of(root).toAbsolutePath().normalize();
    }
    private Path root() {
        try {
            Path profile=Path.of(RuoYiConfig.getProfile()).toFile().getCanonicalFile().toPath();
            Path root=(configuredRoot==null?Path.of(profile+"-private"):configuredRoot).toFile().getCanonicalFile().toPath();
            if(root.startsWith(profile)) throw new ServiceException("私有文件目录不能位于公开上传目录内");
            return root;
        }catch(IOException e){throw new ServiceException("私有文件目录不可用");}
    }
    public Map<String,Object> upload(long user,MultipartFile file) throws IOException {
        String name=file.getOriginalFilename();
        if(name==null || !name.toLowerCase(Locale.ROOT).endsWith(".pdf") || file.isEmpty() || file.getSize()>50L*1024*1024)
            throw new ServiceException("请上传50MB以内的PDF文件");
        byte[] bytes=file.getBytes();
        if(bytes.length<5 || !new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
            throw new ServiceException("文件不是有效PDF");
        String key="shop/"+user+"/"+UUID.randomUUID().toString().replace("-","")+".pdf";
        Path target=root().resolve(key);
        Files.createDirectories(target.getParent());
        if(!target.getParent().toRealPath().startsWith(root().toRealPath()))throw new ServiceException("私有文件路径无效");
        Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
        return Map.of("filePath",key,"fileName",Path.of(name.replace('\\','/')).getFileName().toString(),"fileSize",String.valueOf(bytes.length));
    }
    public void validateKey(String key,long user,boolean admin) {
        if(key==null || !key.matches("shop/[0-9]+/[a-f0-9]{32}\\.pdf") || (!admin && !key.startsWith("shop/"+user+"/")))
            throw new ServiceException("请使用私有PDF上传接口，并选择自己的文件");
        resolve(key);
    }
    public Path resolve(String key) {
        try {
            Path result;
            if(key!=null && key.matches("shop/[0-9]+/[a-f0-9]{32}\\.pdf")) {
                Path root=root().toRealPath(); result=root.resolve(key).toRealPath();
                if(!result.startsWith(root)) throw new IOException();
            } else if(key!=null && key.startsWith("/profile/") && key.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                // Existing snapshots retain their metadata. Public PDF requests are blocked by ShopPdfGuard.
                Path profile=Path.of(RuoYiConfig.getProfile()).toRealPath();
                result=profile.resolve(key.substring("/profile/".length())).toRealPath();
                if(!result.startsWith(profile)) throw new IOException();
            } else throw new IOException();
            if(!Files.isRegularFile(result)) throw new IOException();
            return result;
        } catch(IOException e) {throw new ServiceException("资料文件不存在，请联系管理员");}
    }
}

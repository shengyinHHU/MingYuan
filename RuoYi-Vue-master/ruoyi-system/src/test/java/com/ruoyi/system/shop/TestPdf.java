package com.ruoyi.system.shop;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;
record TestPdf(String name,String contents) implements MultipartFile {
    public String getName(){return "file";}
    public String getOriginalFilename(){return name;}
    public String getContentType(){return "application/pdf";}
    public boolean isEmpty(){return contents.isEmpty();}
    public long getSize(){return getBytes().length;}
    public byte[] getBytes(){return contents.getBytes(StandardCharsets.UTF_8);}
    public InputStream getInputStream(){return new ByteArrayInputStream(getBytes());}
    public void transferTo(File file)throws IOException{Files.write(file.toPath(),getBytes());}
}

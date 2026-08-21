package com.ruoyi.common.core.domain.model;

/**
 * MiniApp material upload request (teacher/admin).
 */
public class MiniAppMaterialUploadBody
{
    /** 资料标题 */
    private String title;

    /** 学科 */
    private String subjectName;

    /** 年级 */
    private String gradeName;

    /** 价格（元） */
    private java.math.BigDecimal price;

    /** 简介 */
    private String intro;

    /** 资料文件相对路径（由 /common/upload 返回） */
    private String filePath;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 封面图URL（可选） */
    private String coverUrl;

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSubjectName()
    {
        return subjectName;
    }

    public void setSubjectName(String subjectName)
    {
        this.subjectName = subjectName;
    }

    public String getGradeName()
    {
        return gradeName;
    }

    public void setGradeName(String gradeName)
    {
        this.gradeName = gradeName;
    }

    public java.math.BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(java.math.BigDecimal price)
    {
        this.price = price;
    }

    public String getIntro()
    {
        return intro;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }
}

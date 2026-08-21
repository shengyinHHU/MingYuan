package com.ruoyi.system.domain;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 资料商品对象 edu_material
 *
 * @author ruoyi
 * @date 2026-08-21
 */
public class EduMaterial extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 资料ID */
    private Long materialId;

    /** 资料编码 */
    @Excel(name = "资料编码")
    private String materialCode;

    /** 资料标题 */
    @Excel(name = "资料标题")
    private String title;

    /** 学科 */
    @Excel(name = "学科")
    private String subjectName;

    /** 年级 */
    @Excel(name = "年级")
    private String gradeName;

    /** 价格（元） */
    @Excel(name = "价格", readConverterExp = "0=免费")
    private BigDecimal price;

    /** 简介 */
    @Excel(name = "简介")
    private String intro;

    /** 资料文件相对路径 */
    @Excel(name = "文件路径")
    private String filePath;

    /** 原始文件名 */
    @Excel(name = "文件名")
    private String fileName;

    /** 文件大小（字节） */
    @Excel(name = "文件大小")
    private Long fileSize;

    /** 封面图URL */
    @Excel(name = "封面图URL")
    private String coverUrl;

    /** 上架状态（0下架 1上架） */
    @Excel(name = "上架状态", readConverterExp = "0=下架,1=上架")
    private String shelfStatus;

    /** 销量 */
    @Excel(name = "销量")
    private Integer saleCount;

    /** 上传者用户ID */
    @Excel(name = "上传者用户ID")
    private Long uploadUserId;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 上传者昵称（关联查询，非表字段） */
    private String uploadUserName;

    /** 当前家长是否已购买（0未购买 1已购买，小程序接口使用，非表字段） */
    private String bought;

    public void setMaterialId(Long materialId)
    {
        this.materialId = materialId;
    }

    public Long getMaterialId()
    {
        return materialId;
    }

    public void setMaterialCode(String materialCode)
    {
        this.materialCode = materialCode;
    }

    public String getMaterialCode()
    {
        return materialCode;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return title;
    }

    public void setSubjectName(String subjectName)
    {
        this.subjectName = subjectName;
    }

    public String getSubjectName()
    {
        return subjectName;
    }

    public void setGradeName(String gradeName)
    {
        this.gradeName = gradeName;
    }

    public String getGradeName()
    {
        return gradeName;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
    }

    public String getIntro()
    {
        return intro;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setShelfStatus(String shelfStatus)
    {
        this.shelfStatus = shelfStatus;
    }

    public String getShelfStatus()
    {
        return shelfStatus;
    }

    public void setSaleCount(Integer saleCount)
    {
        this.saleCount = saleCount;
    }

    public Integer getSaleCount()
    {
        return saleCount;
    }

    public void setUploadUserId(Long uploadUserId)
    {
        this.uploadUserId = uploadUserId;
    }

    public Long getUploadUserId()
    {
        return uploadUserId;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setUploadUserName(String uploadUserName)
    {
        this.uploadUserName = uploadUserName;
    }

    public String getUploadUserName()
    {
        return uploadUserName;
    }

    public void setBought(String bought)
    {
        this.bought = bought;
    }

    public String getBought()
    {
        return bought;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("materialId", getMaterialId())
            .append("materialCode", getMaterialCode())
            .append("title", getTitle())
            .append("subjectName", getSubjectName())
            .append("gradeName", getGradeName())
            .append("price", getPrice())
            .append("intro", getIntro())
            .append("filePath", getFilePath())
            .append("fileName", getFileName())
            .append("fileSize", getFileSize())
            .append("coverUrl", getCoverUrl())
            .append("shelfStatus", getShelfStatus())
            .append("saleCount", getSaleCount())
            .append("uploadUserId", getUploadUserId())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}

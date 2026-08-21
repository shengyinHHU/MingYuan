package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 课程报名对象 edu_enrollment
 * 
 * @author ruoyi
 * @date 2026-08-05
 */
public class EduEnrollment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 报名ID */
    private Long enrollmentId;

    /** 报名编码 */
    @Excel(name = "报名编码")
    private String enrollmentCode;

    /** 排课ID */
    @Excel(name = "排课ID")
    private Long scheduleId;

    /** 家长用户ID */
    @Excel(name = "家长用户ID")
    private Long parentId;

    /** 学生姓名 */
    @Excel(name = "学生姓名")
    private String studentName;

    /** 学生手机号 */
    @Excel(name = "学生手机号")
    private String studentPhone;

    /** 联系电话 */
    @Excel(name = "联系电话")
    private String contactPhone;

    /** 报名状态（0待确认 1报名成功 2已取消） */
    @Excel(name = "报名状态", readConverterExp = "0=待确认,1=报名成功,2=已取消")
    private String enrollmentStatus;

    /** 支付状态（0未支付 1已支付 2已退款） */
    @Excel(name = "支付状态", readConverterExp = "0=未支付,1=已支付,2=已退款")
    private String payStatus;

    /** 取消时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "取消时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date cancelTime;

    /** 取消原因 */
    @Excel(name = "取消原因")
    private String cancelReason;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    public void setEnrollmentId(Long enrollmentId) 
    {
        this.enrollmentId = enrollmentId;
    }

    public Long getEnrollmentId() 
    {
        return enrollmentId;
    }

    public void setEnrollmentCode(String enrollmentCode) 
    {
        this.enrollmentCode = enrollmentCode;
    }

    public String getEnrollmentCode() 
    {
        return enrollmentCode;
    }

    public void setScheduleId(Long scheduleId) 
    {
        this.scheduleId = scheduleId;
    }

    public Long getScheduleId() 
    {
        return scheduleId;
    }

    public void setParentId(Long parentId) 
    {
        this.parentId = parentId;
    }

    public Long getParentId() 
    {
        return parentId;
    }

    public void setStudentName(String studentName) 
    {
        this.studentName = studentName;
    }

    public String getStudentName() 
    {
        return studentName;
    }

    public void setStudentPhone(String studentPhone) 
    {
        this.studentPhone = studentPhone;
    }

    public String getStudentPhone() 
    {
        return studentPhone;
    }

    public void setContactPhone(String contactPhone) 
    {
        this.contactPhone = contactPhone;
    }

    public String getContactPhone() 
    {
        return contactPhone;
    }

    public void setEnrollmentStatus(String enrollmentStatus) 
    {
        this.enrollmentStatus = enrollmentStatus;
    }

    public String getEnrollmentStatus() 
    {
        return enrollmentStatus;
    }

    public void setPayStatus(String payStatus) 
    {
        this.payStatus = payStatus;
    }

    public String getPayStatus() 
    {
        return payStatus;
    }

    public void setCancelTime(Date cancelTime) 
    {
        this.cancelTime = cancelTime;
    }

    public Date getCancelTime() 
    {
        return cancelTime;
    }

    public void setCancelReason(String cancelReason) 
    {
        this.cancelReason = cancelReason;
    }

    public String getCancelReason() 
    {
        return cancelReason;
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

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("enrollmentId", getEnrollmentId())
            .append("enrollmentCode", getEnrollmentCode())
            .append("scheduleId", getScheduleId())
            .append("parentId", getParentId())
            .append("studentName", getStudentName())
            .append("studentPhone", getStudentPhone())
            .append("contactPhone", getContactPhone())
            .append("enrollmentStatus", getEnrollmentStatus())
            .append("payStatus", getPayStatus())
            .append("cancelTime", getCancelTime())
            .append("cancelReason", getCancelReason())
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

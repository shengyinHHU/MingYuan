package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 课程上课记录对象 edu_attendance
 * 
 * @author ruoyi
 * @date 2026-08-06
 */
public class EduAttendance extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 上课记录ID */
    private Long attendanceId;

    /** 上课记录编码 */
    @Excel(name = "上课记录编码")
    private String attendanceCode;

    /** 排课ID */
    @Excel(name = "排课ID")
    private Long scheduleId;

    /** 报名ID */
    @Excel(name = "报名ID")
    private Long enrollmentId;

    /** 家长用户ID */
    @Excel(name = "家长用户ID")
    private Long parentId;

    /** 学生姓名快照 */
    @Excel(name = "学生姓名快照")
    private String studentName;

    /** 上课状态（0未上课 1已上课 2已取消） */
    @Excel(name = "上课状态", readConverterExp = "0=未上课,1=已上课,2=已取消")
    private String attendanceStatus;

    /** 确认上课时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "确认上课时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date attendedTime;

    /** 确认人用户ID */
    @Excel(name = "确认人用户ID")
    private Long confirmBy;

    /** 确认人姓名 */
    @Excel(name = "确认人姓名")
    private String confirmName;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    public void setAttendanceId(Long attendanceId) 
    {
        this.attendanceId = attendanceId;
    }

    public Long getAttendanceId() 
    {
        return attendanceId;
    }

    public void setAttendanceCode(String attendanceCode) 
    {
        this.attendanceCode = attendanceCode;
    }

    public String getAttendanceCode() 
    {
        return attendanceCode;
    }

    public void setScheduleId(Long scheduleId) 
    {
        this.scheduleId = scheduleId;
    }

    public Long getScheduleId() 
    {
        return scheduleId;
    }

    public void setEnrollmentId(Long enrollmentId) 
    {
        this.enrollmentId = enrollmentId;
    }

    public Long getEnrollmentId() 
    {
        return enrollmentId;
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

    public void setAttendanceStatus(String attendanceStatus) 
    {
        this.attendanceStatus = attendanceStatus;
    }

    public String getAttendanceStatus() 
    {
        return attendanceStatus;
    }

    public void setAttendedTime(Date attendedTime) 
    {
        this.attendedTime = attendedTime;
    }

    public Date getAttendedTime() 
    {
        return attendedTime;
    }

    public void setConfirmBy(Long confirmBy) 
    {
        this.confirmBy = confirmBy;
    }

    public Long getConfirmBy() 
    {
        return confirmBy;
    }

    public void setConfirmName(String confirmName) 
    {
        this.confirmName = confirmName;
    }

    public String getConfirmName() 
    {
        return confirmName;
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
            .append("attendanceId", getAttendanceId())
            .append("attendanceCode", getAttendanceCode())
            .append("scheduleId", getScheduleId())
            .append("enrollmentId", getEnrollmentId())
            .append("parentId", getParentId())
            .append("studentName", getStudentName())
            .append("attendanceStatus", getAttendanceStatus())
            .append("attendedTime", getAttendedTime())
            .append("confirmBy", getConfirmBy())
            .append("confirmName", getConfirmName())
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

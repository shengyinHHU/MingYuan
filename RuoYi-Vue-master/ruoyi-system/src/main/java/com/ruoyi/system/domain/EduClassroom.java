package com.ruoyi.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 教室信息对象 edu_classroom
 * 
 * @author ruoyi
 * @date 2026-08-01
 */
public class EduClassroom extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 教室ID */
    private Long classroomId;

    /** 教室编码 */
    @Excel(name = "教室编码")
    private String classroomCode;

    /** 校区名称 */
    @Excel(name = "校区名称")
    private String campusName;

    /** 教室名称 */
    @Excel(name = "教室名称")
    private String classroomName;

    /** 教室规格（大/中/小） */
    @Excel(name = "教室规格", readConverterExp = "大=/中/小")
    private String classroomSize;

    /** 建议容量 */
    @Excel(name = "建议容量")
    private Long capacity;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public void setClassroomId(Long classroomId) 
    {
        this.classroomId = classroomId;
    }

    public Long getClassroomId() 
    {
        return classroomId;
    }

    public void setClassroomCode(String classroomCode) 
    {
        this.classroomCode = classroomCode;
    }

    public String getClassroomCode() 
    {
        return classroomCode;
    }

    public void setCampusName(String campusName) 
    {
        this.campusName = campusName;
    }

    public String getCampusName() 
    {
        return campusName;
    }

    public void setClassroomName(String classroomName) 
    {
        this.classroomName = classroomName;
    }

    public String getClassroomName() 
    {
        return classroomName;
    }

    public void setClassroomSize(String classroomSize) 
    {
        this.classroomSize = classroomSize;
    }

    public String getClassroomSize() 
    {
        return classroomSize;
    }

    public void setCapacity(Long capacity) 
    {
        this.capacity = capacity;
    }

    public Long getCapacity() 
    {
        return capacity;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("classroomId", getClassroomId())
            .append("classroomCode", getClassroomCode())
            .append("campusName", getCampusName())
            .append("classroomName", getClassroomName())
            .append("classroomSize", getClassroomSize())
            .append("capacity", getCapacity())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}

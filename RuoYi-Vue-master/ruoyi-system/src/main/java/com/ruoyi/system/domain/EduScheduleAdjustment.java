package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 排课调课记录对象 edu_schedule_adjustment
 *
 * @author ruoyi
 * @date 2026-08-26
 */
public class EduScheduleAdjustment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 调课ID */
    @Excel(name = "调课ID")
    private Long adjustmentId;

    /** 关联排课ID */
    @Excel(name = "排课ID")
    private Long scheduleId;

    /** 原上课日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "原上课日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date originalDate;

    /** 调整后日期（NULL=停课） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "调整后日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date adjustedDate;

    /** 调课原因（国庆节/元旦等） */
    @Excel(name = "调课原因")
    private String reason;

    public void setAdjustmentId(Long adjustmentId)
    {
        this.adjustmentId = adjustmentId;
    }

    public Long getAdjustmentId()
    {
        return adjustmentId;
    }

    public void setScheduleId(Long scheduleId)
    {
        this.scheduleId = scheduleId;
    }

    public Long getScheduleId()
    {
        return scheduleId;
    }

    public void setOriginalDate(Date originalDate)
    {
        this.originalDate = originalDate;
    }

    public Date getOriginalDate()
    {
        return originalDate;
    }

    public void setAdjustedDate(Date adjustedDate)
    {
        this.adjustedDate = adjustedDate;
    }

    public Date getAdjustedDate()
    {
        return adjustedDate;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getReason()
    {
        return reason;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("adjustmentId", getAdjustmentId())
            .append("scheduleId", getScheduleId())
            .append("originalDate", getOriginalDate())
            .append("adjustedDate", getAdjustedDate())
            .append("reason", getReason())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}

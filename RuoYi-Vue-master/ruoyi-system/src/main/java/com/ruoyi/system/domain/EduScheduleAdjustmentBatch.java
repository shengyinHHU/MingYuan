package com.ruoyi.system.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 批量调课请求对象
 *
 * 场景：节假日把某学期+期次（如 秋季·周六）日期范围内的全部上课日
 * 统一调整到新日期或停课，按命中的每条排课×每个上课日生成调课记录
 *
 * @author ruoyi
 * @date 2026-08-26
 */
public class EduScheduleAdjustmentBatch implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 学期（必填，如：秋季） */
    private String termName;

    /** 期次或上课日（必填，如：周六） */
    private String periodName;

    /** 年级（可选筛选） */
    private String gradeName;

    /** 科目（可选筛选） */
    private String subjectName;

    /** 原上课日期范围-开始（必填） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date originalStartDate;

    /** 原上课日期范围-结束（必填） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date originalEndDate;

    /** 调整方式：MOVE_TO调至指定日期 / POSTPONE顺延N天 / CANCEL停课 */
    private String adjustMode;

    /** 顺延天数（POSTPONE时必填） */
    private Integer offsetDays;

    /** 目标日期（MOVE_TO时必填：范围内第一个上课日调到该日期，其余保持间隔依次顺移） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date targetDate;

    /** 调课原因 */
    private String reason;

    public String getTermName()
    {
        return termName;
    }

    public void setTermName(String termName)
    {
        this.termName = termName;
    }

    public String getPeriodName()
    {
        return periodName;
    }

    public void setPeriodName(String periodName)
    {
        this.periodName = periodName;
    }

    public String getGradeName()
    {
        return gradeName;
    }

    public void setGradeName(String gradeName)
    {
        this.gradeName = gradeName;
    }

    public String getSubjectName()
    {
        return subjectName;
    }

    public void setSubjectName(String subjectName)
    {
        this.subjectName = subjectName;
    }

    public Date getOriginalStartDate()
    {
        return originalStartDate;
    }

    public void setOriginalStartDate(Date originalStartDate)
    {
        this.originalStartDate = originalStartDate;
    }

    public Date getOriginalEndDate()
    {
        return originalEndDate;
    }

    public void setOriginalEndDate(Date originalEndDate)
    {
        this.originalEndDate = originalEndDate;
    }

    public String getAdjustMode()
    {
        return adjustMode;
    }

    public void setAdjustMode(String adjustMode)
    {
        this.adjustMode = adjustMode;
    }

    public Integer getOffsetDays()
    {
        return offsetDays;
    }

    public void setOffsetDays(Integer offsetDays)
    {
        this.offsetDays = offsetDays;
    }

    public Date getTargetDate()
    {
        return targetDate;
    }

    public void setTargetDate(Date targetDate)
    {
        this.targetDate = targetDate;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }
}

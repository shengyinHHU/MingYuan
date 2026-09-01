package com.ruoyi.common.core.domain.model;

import java.util.List;

/**
 * 小程序课次复核确认体（管理员）
 */
public class MiniAppSignReviewBody
{
    /** 排课ID */
    private Long scheduleId;

    /** 课次日期（yyyy-MM-dd） */
    private String classDate;

    /** 课堂图片路径列表 */
    private List<String> images;

    public void setScheduleId(Long scheduleId)
    {
        this.scheduleId = scheduleId;
    }

    public Long getScheduleId()
    {
        return scheduleId;
    }

    public void setClassDate(String classDate)
    {
        this.classDate = classDate;
    }

    public String getClassDate()
    {
        return classDate;
    }

    public void setImages(List<String> images)
    {
        this.images = images;
    }

    public List<String> getImages()
    {
        return images;
    }
}

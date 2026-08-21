package com.ruoyi.common.core.domain.model;

/**
 * MiniApp parent enrollment request.
 */
public class MiniAppEnrollmentBody
{
    private Long scheduleId;

    private String studentName;

    private String studentPhone;

    private String contactPhone;

    public Long getScheduleId()
    {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId)
    {
        this.scheduleId = scheduleId;
    }

    public String getStudentName()
    {
        return studentName;
    }

    public void setStudentName(String studentName)
    {
        this.studentName = studentName;
    }

    public String getStudentPhone()
    {
        return studentPhone;
    }

    public void setStudentPhone(String studentPhone)
    {
        this.studentPhone = studentPhone;
    }

    public String getContactPhone()
    {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone)
    {
        this.contactPhone = contactPhone;
    }
}

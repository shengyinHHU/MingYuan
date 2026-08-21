package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.common.core.domain.model.MiniAppEnrollmentBody;

/**
 * MiniApp parent service.
 */
public interface IMiniAppParentService
{
    public List<Map<String, Object>> selectAvailableSchedules(String gradeName, String subjectName, String termName);

    public List<Map<String, Object>> selectMyEnrollments();

    public List<Map<String, Object>> selectMyTimetable();

    public List<Map<String, Object>> selectMyAttendanceSummary();

    public List<Map<String, Object>> selectMyAttendanceDetails(String courseClassName);

    public int enroll(MiniAppEnrollmentBody body);
}

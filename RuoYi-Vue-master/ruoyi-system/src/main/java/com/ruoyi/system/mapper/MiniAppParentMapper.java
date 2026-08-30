package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * MiniApp parent course enrollment mapper.
 */
public interface MiniAppParentMapper
{
    public List<Map<String, Object>> selectAvailableSchedules(@Param("parentId") Long parentId,
            @Param("gradeName") String gradeName, @Param("subjectName") String subjectName,
            @Param("termName") String termName);

    public List<Map<String, Object>> selectParentEnrollments(@Param("parentId") Long parentId);

    public List<Map<String, Object>> selectParentTimetable(@Param("parentId") Long parentId);

    public List<Map<String, Object>> selectScheduleAdjustments(@Param("scheduleId") Object scheduleId);

    public List<Map<String, Object>> selectParentAttendanceSummary(@Param("parentId") Long parentId);

    public List<Map<String, Object>> selectParentAttendanceDetails(@Param("parentId") Long parentId,
            @Param("courseClassName") String courseClassName);

    public int countScheduleAvailable(@Param("scheduleId") Long scheduleId);

    public int countActiveEnrollment(@Param("scheduleId") Long scheduleId, @Param("parentId") Long parentId,
            @Param("studentName") String studentName);

    public int increaseScheduleEnrollment(@Param("scheduleId") Long scheduleId);

    public int insertParentEnrollment(@Param("enrollmentCode") String enrollmentCode,
            @Param("scheduleId") Long scheduleId, @Param("parentId") Long parentId,
            @Param("studentName") String studentName, @Param("studentPhone") String studentPhone,
            @Param("contactPhone") String contactPhone, @Param("createBy") String createBy);

    public Long selectEnrollmentIdByCode(@Param("enrollmentCode") String enrollmentCode);

    public int insertInitialAttendance(@Param("attendanceCode") String attendanceCode,
            @Param("scheduleId") Long scheduleId, @Param("enrollmentId") Long enrollmentId,
            @Param("parentId") Long parentId, @Param("studentName") String studentName,
            @Param("createBy") String createBy);

    /** 查询报名记录的所属家长ID与排课ID（用于取消前校验归属） */
    public Map<String, Object> selectEnrollmentOwnerInfo(@Param("enrollmentId") Long enrollmentId);

    /** 家长端逻辑取消报名：del_flag='2', enrollment_status='2', cancel_time=now() */
    public int cancelEnrollmentById(@Param("enrollmentId") Long enrollmentId,
            @Param("updateBy") String updateBy);

    /** 取消报名后释放排课名额：enrolled_count-1，满班时自动恢复为招生中 */
    public int decreaseScheduleEnrollment(@Param("scheduleId") Long scheduleId,
            @Param("updateBy") String updateBy);

    /** 取消报名时同步逻辑删除对应考勤记录：del_flag='2' */
    public int cancelAttendanceByEnrollmentId(@Param("enrollmentId") Long enrollmentId,
            @Param("updateBy") String updateBy);
}

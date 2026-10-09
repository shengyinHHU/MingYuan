package com.ruoyi.system.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.model.MiniAppEnrollmentBody;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.mapper.MiniAppParentMapper;
import com.ruoyi.system.service.IMiniAppParentService;
import com.ruoyi.system.service.IEduEnrollmentService;

/**
 * MiniApp parent service implementation.
 */
@Service
public class MiniAppParentServiceImpl implements IMiniAppParentService
{
    @Autowired
    private MiniAppParentMapper miniAppParentMapper;

    @Autowired
    private IEduEnrollmentService enrollmentService;

    @Override
    public List<Map<String, Object>> selectAvailableSchedules(String gradeName, String subjectName, String termName)
    {
        return miniAppParentMapper.selectAvailableSchedules(SecurityUtils.getUserId(), gradeName, subjectName, termName);
    }

    @Override
    public List<Map<String, Object>> selectMyEnrollments()
    {
        return miniAppParentMapper.selectParentEnrollments(SecurityUtils.getUserId());
    }

    @Override
    public List<Map<String, Object>> selectMyTimetable()
    {
        List<Map<String, Object>> list = miniAppParentMapper.selectParentTimetable(SecurityUtils.getUserId());
        if (list != null)
        {
            for (Map<String, Object> item : list)
            {
                Object scheduleId = item.get("scheduleId");
                if (scheduleId != null)
                {
                    item.put("adjustments", miniAppParentMapper.selectScheduleAdjustments(scheduleId));
                }
            }
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> selectMyAttendanceSummary()
    {
        return miniAppParentMapper.selectParentAttendanceSummary(SecurityUtils.getUserId());
    }

    @Override
    public List<Map<String, Object>> selectMyAttendanceDetails(String courseClassName)
    {
        return miniAppParentMapper.selectParentAttendanceDetails(SecurityUtils.getUserId(), courseClassName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int enroll(MiniAppEnrollmentBody body)
    {
        if (body == null || body.getScheduleId() == null)
        {
            throw new ServiceException("Please select a schedule");
        }
        String studentName = StringUtils.trim(body.getStudentName());
        if (StringUtils.isBlank(studentName))
        {
            throw new ServiceException("Please enter student name");
        }
        if (studentName.length() > 30)
        {
            throw new ServiceException("Student name is too long");
        }

        Long parentId = SecurityUtils.getUserId();
        if (miniAppParentMapper.countScheduleAvailable(body.getScheduleId()) == 0)
        {
            throw new ServiceException("This schedule is not available");
        }
        if (miniAppParentMapper.countActiveEnrollment(body.getScheduleId(), parentId, studentName) > 0)
        {
            throw new ServiceException("This student has already enrolled in this schedule");
        }
        if (miniAppParentMapper.increaseScheduleEnrollment(body.getScheduleId()) == 0)
        {
            throw new ServiceException("This schedule is full");
        }

        String enrollmentCode = "ENR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + IdUtils.fastSimpleUUID().substring(0, 6).toUpperCase();
        String contactPhone = StringUtils.defaultString(body.getContactPhone(), SecurityUtils.getLoginUser().getUser().getPhonenumber());
        int rows = miniAppParentMapper.insertParentEnrollment(enrollmentCode, body.getScheduleId(), parentId, studentName,
                StringUtils.defaultString(body.getStudentPhone()), contactPhone, SecurityUtils.getUsername());
        Long enrollmentId = miniAppParentMapper.selectEnrollmentIdByCode(enrollmentCode);
        if (enrollmentId != null)
        {
            String attendanceCode = "ATT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + IdUtils.fastSimpleUUID().substring(0, 6).toUpperCase();
            miniAppParentMapper.insertInitialAttendance(attendanceCode, body.getScheduleId(), enrollmentId, parentId,
                    studentName, SecurityUtils.getUsername());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelEnrollment(Long enrollmentId)
    {
        if (enrollmentId == null)
        {
            throw new ServiceException("请选择要取消的课程");
        }
        Long parentId = SecurityUtils.getUserId();
        Map<String, Object> owner = miniAppParentMapper.selectEnrollmentOwnerInfo(enrollmentId);
        if (owner == null)
        {
            throw new ServiceException("报名记录不存在或已取消");
        }
        Object recordParentId = owner.get("parentId");
        Object delFlag = owner.get("delFlag");
        if (recordParentId == null || !parentId.equals(Long.valueOf(String.valueOf(recordParentId))))
        {
            throw new ServiceException("无权取消他人的报名");
        }
        if (delFlag != null && !"0".equals(String.valueOf(delFlag)))
        {
            throw new ServiceException("该报名已取消，请勿重复操作");
        }
        return enrollmentService.cancelEduEnrollmentByEnrollmentIds(new Long[] { enrollmentId });
    }
}

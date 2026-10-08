package com.ruoyi.system.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.EduEnrollment;
import com.ruoyi.system.mapper.MiniAppAdminEnrollmentMapper;
import com.ruoyi.system.mapper.MiniAppParentMapper;
import com.ruoyi.system.service.IEduEnrollmentService;

/** 教师课程和班级学员只读视图，复用报名、排课及课次历史。 */
@RestController
@RequestMapping("/miniapp/teacher/course")
@PreAuthorize("@ss.hasRole('teacher')")
public class MiniAppTeacherCourseController extends BaseController
{
    @Autowired private MiniAppAdminEnrollmentMapper viewMapper;
    @Autowired private MiniAppParentMapper parentMapper;
    @Autowired private IEduEnrollmentService enrollmentService;

    @GetMapping("/schedules")
    public AjaxResult schedules()
    {
        List<Map<String, Object>> courses = viewMapper.selectTeacherSchedules(SecurityUtils.getUserId(), null);
        for (Map<String, Object> course : courses)
        {
            course.put("adjustments", parentMapper.selectScheduleAdjustments(course.get("scheduleId")));
        }
        return success(courses);
    }

    @GetMapping("/students")
    public TableDataInfo students(@RequestParam(required = false) Long scheduleId,
                                 @RequestParam(defaultValue = "") String keyword)
    {
        if (scheduleId != null) ownSchedule(scheduleId);
        EduEnrollment query = query();
        query.setScheduleId(scheduleId);
        query.getParams().put("adminKeyword", keyword.trim());
        startPage();
        return getDataTable(enrollmentService.selectEduEnrollmentList(query));
    }

    @GetMapping("/schedule/{scheduleId}")
    public AjaxResult schedule(@PathVariable Long scheduleId)
    {
        return success(ownSchedule(scheduleId));
    }

    @GetMapping("/student/{enrollmentId}")
    public AjaxResult student(@PathVariable Long enrollmentId)
    {
        EduEnrollment query = query();
        query.getParams().put("teacherEnrollmentId", enrollmentId);
        List<EduEnrollment> rows = enrollmentService.selectEduEnrollmentList(query);
        if (rows.isEmpty()) throw new ServiceException("学员不存在或不属于本人课程");
        EduEnrollment enrollment = rows.get(0);
        Map<String, Object> data = new HashMap<>();
        data.put("enrollment", enrollment);
        data.put("schedule", ownSchedule(enrollment.getScheduleId()));
        data.put("history", viewMapper.selectTeacherHistory(enrollmentId, SecurityUtils.getUserId()));
        return success(data);
    }

    private EduEnrollment query()
    {
        EduEnrollment query = new EduEnrollment();
        query.setDelFlag("0");
        query.getParams().put("teacherUserId", SecurityUtils.getUserId());
        return query;
    }

    private Map<String, Object> ownSchedule(Long scheduleId)
    {
        List<Map<String, Object>> courses = viewMapper.selectTeacherSchedules(SecurityUtils.getUserId(), scheduleId);
        if (courses.isEmpty()) throw new ServiceException("课程不存在或不属于本人");
        return courses.get(0);
    }
}

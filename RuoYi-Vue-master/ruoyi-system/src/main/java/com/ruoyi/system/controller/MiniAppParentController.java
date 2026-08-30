package com.ruoyi.system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.MiniAppEnrollmentBody;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.service.IMiniAppParentService;

/**
 * MiniApp parent enrollment APIs.
 */
@RestController
@RequestMapping("/miniapp/parent")
public class MiniAppParentController extends BaseController
{
    @Autowired
    private IMiniAppParentService miniAppParentService;

    @PreAuthorize("@ss.hasAnyRoles('parent')")
    @GetMapping("/schedules")
    public AjaxResult schedules(@RequestParam(value = "gradeName", required = false) String gradeName,
            @RequestParam(value = "subjectName", required = false) String subjectName,
            @RequestParam(value = "termName", required = false) String termName)
    {
        return success(miniAppParentService.selectAvailableSchedules(gradeName, subjectName, termName));
    }

    @PreAuthorize("@ss.hasAnyRoles('parent')")
    @GetMapping("/enrollments")
    public AjaxResult enrollments()
    {
        return success(miniAppParentService.selectMyEnrollments());
    }

    @PreAuthorize("@ss.hasAnyRoles('parent')")
    @GetMapping("/timetable")
    public AjaxResult timetable()
    {
        return success(miniAppParentService.selectMyTimetable());
    }

    @PreAuthorize("@ss.hasAnyRoles('parent')")
    @GetMapping("/attendance")
    public AjaxResult attendance()
    {
        return success(miniAppParentService.selectMyAttendanceSummary());
    }

    @PreAuthorize("@ss.hasAnyRoles('parent')")
    @GetMapping("/attendance/details")
    public AjaxResult attendanceDetails(@RequestParam("courseClassName") String courseClassName)
    {
        return success(miniAppParentService.selectMyAttendanceDetails(courseClassName));
    }

    @PreAuthorize("@ss.hasAnyRoles('parent')")
    @PostMapping("/enroll")
    public AjaxResult enroll(@RequestBody MiniAppEnrollmentBody body)
    {
        return toAjax(miniAppParentService.enroll(body));
    }

    /**
     * 家长取消本人的课程报名
     * 逻辑：校验归属本人→逻辑删除报名记录→释放排课名额→同步删除考勤记录
     */
    @PreAuthorize("@ss.hasAnyRoles('parent')")
    @Log(title = "家长取消报名", businessType = BusinessType.DELETE)
    @DeleteMapping("/enrollment/{enrollmentId}")
    public AjaxResult cancelEnrollment(@PathVariable("enrollmentId") Long enrollmentId)
    {
        return toAjax(miniAppParentService.cancelEnrollment(enrollmentId));
    }
}

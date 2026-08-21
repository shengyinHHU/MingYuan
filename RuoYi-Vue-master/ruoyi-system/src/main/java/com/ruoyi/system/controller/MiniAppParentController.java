package com.ruoyi.system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.MiniAppEnrollmentBody;
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

    @PreAuthorize("@ss.hasRole('parent')")
    @GetMapping("/schedules")
    public AjaxResult schedules(@RequestParam(value = "gradeName", required = false) String gradeName,
            @RequestParam(value = "subjectName", required = false) String subjectName,
            @RequestParam(value = "termName", required = false) String termName)
    {
        return success(miniAppParentService.selectAvailableSchedules(gradeName, subjectName, termName));
    }

    @PreAuthorize("@ss.hasRole('parent')")
    @GetMapping("/enrollments")
    public AjaxResult enrollments()
    {
        return success(miniAppParentService.selectMyEnrollments());
    }

    @PreAuthorize("@ss.hasRole('parent')")
    @GetMapping("/timetable")
    public AjaxResult timetable()
    {
        return success(miniAppParentService.selectMyTimetable());
    }

    @PreAuthorize("@ss.hasRole('parent')")
    @GetMapping("/attendance")
    public AjaxResult attendance()
    {
        return success(miniAppParentService.selectMyAttendanceSummary());
    }

    @PreAuthorize("@ss.hasRole('parent')")
    @GetMapping("/attendance/details")
    public AjaxResult attendanceDetails(@RequestParam("courseClassName") String courseClassName)
    {
        return success(miniAppParentService.selectMyAttendanceDetails(courseClassName));
    }

    @PreAuthorize("@ss.hasRole('parent')")
    @PostMapping("/enroll")
    public AjaxResult enroll(@RequestBody MiniAppEnrollmentBody body)
    {
        return toAjax(miniAppParentService.enroll(body));
    }
}

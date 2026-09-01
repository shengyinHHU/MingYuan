package com.ruoyi.system.controller;

import java.util.List;
import java.util.Map;
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
import com.ruoyi.common.core.domain.model.MiniAppSignSubmitBody;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.EduClassSignIn;
import com.ruoyi.system.service.IEduClassSignInService;

/**
 * 小程序 教师上课签到
 */
@RestController
@RequestMapping("/miniapp/teacher/sign")
public class MiniAppTeacherSignController extends BaseController
{
    @Autowired
    private IEduClassSignInService signInService;

    /**
     * 我的排课列表（含最近一次签到汇总）
     */
    @PreAuthorize("@ss.hasAnyRoles('teacher,admin')")
    @GetMapping("/list")
    public AjaxResult list()
    {
        List<EduClassSignIn> list = signInService.selectTeacherSchedules(SecurityUtils.getUserId());
        return success(list);
    }

    /**
     * 某排课的报名学员（预填签到名单）
     */
    @PreAuthorize("@ss.hasAnyRoles('teacher,admin')")
    @GetMapping("/students")
    public AjaxResult students(@RequestParam("scheduleId") Long scheduleId)
    {
        return success(signInService.selectEnrolledStudents(scheduleId));
    }

    /**
     * 某课次的签到明细 + 复核信息
     */
    @PreAuthorize("@ss.hasAnyRoles('teacher,admin')")
    @GetMapping("/detail")
    public AjaxResult detail(@RequestParam("scheduleId") Long scheduleId,
            @RequestParam("classDate") String classDate)
    {
        return success(signInService.selectDetail(scheduleId, classDate));
    }

    /**
     * 提交签到（覆盖式；复核完成后锁定）
     */
    @PreAuthorize("@ss.hasAnyRoles('teacher,admin')")
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody MiniAppSignSubmitBody body)
    {
        Map<String, Object> result = signInService.submitSignIn(body, SecurityUtils.getUsername());
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(result);
        return ajax;
    }
}

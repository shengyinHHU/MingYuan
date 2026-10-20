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
import com.ruoyi.common.core.domain.model.MiniAppSignReviewBody;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.EduClassSignIn;
import com.ruoyi.system.service.IEduClassSignInService;

/**
 * 小程序 教务出勤审核（兼容原管理员路径）
 */
@RestController
@RequestMapping({"/miniapp/admin/sign", "/miniapp/education/sign"})
public class MiniAppAdminSignController extends BaseController
{
    @Autowired
    private IEduClassSignInService signInService;

    @PreAuthorize("@ss.hasAnyRoles('admin,education')")
    @PostMapping("/reject")
    public AjaxResult reject(@RequestBody MiniAppSignReviewBody body)
    {
        return success(signInService.reject(body, SecurityUtils.getUserId(),
            SecurityUtils.getLoginUser().getUser().getNickName()));
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,education')")
    @PostMapping("/correct")
    public AjaxResult correct(@RequestBody MiniAppSignReviewBody body)
    {
        return success(signInService.correct(body, SecurityUtils.getUserId(),
            SecurityUtils.getLoginUser().getUser().getNickName()));
    }

    /**
     * 签到列表（按课次聚合，可按日期过滤）
     */
    @PreAuthorize("@ss.hasAnyRoles('admin,education')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "classDate", required = false) String classDate)
    {
        List<EduClassSignIn> list = signInService.selectReviewList(classDate);
        return success(list);
    }

    /**
     * 某课次的签到明细 + 复核信息
     */
    @PreAuthorize("@ss.hasAnyRoles('admin,education')")
    @GetMapping("/detail")
    public AjaxResult detail(@RequestParam("scheduleId") Long scheduleId,
            @RequestParam("classDate") String classDate)
    {
        return success(signInService.selectDetail(scheduleId, classDate));
    }

    /**
     * 一名教务或管理员确认即完成，重复提交不改变原审核事实。
     */
    @PreAuthorize("@ss.hasAnyRoles('admin,education')")
    @PostMapping("/review")
    public AjaxResult review(@RequestBody MiniAppSignReviewBody body)
    {
        Map<String, Object> result = signInService.review(body, SecurityUtils.getUserId(),
                SecurityUtils.getLoginUser().getUser().getNickName());
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(result);
        return ajax;
    }
}

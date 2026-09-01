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
 * 小程序 管理员签到复核
 */
@RestController
@RequestMapping("/miniapp/admin/sign")
public class MiniAppAdminSignController extends BaseController
{
    @Autowired
    private IEduClassSignInService signInService;

    /**
     * 签到列表（按课次聚合，可按日期过滤）
     */
    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "classDate", required = false) String classDate)
    {
        List<EduClassSignIn> list = signInService.selectReviewList(classDate);
        return success(list);
    }

    /**
     * 某课次的签到明细 + 复核信息
     */
    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("/detail")
    public AjaxResult detail(@RequestParam("scheduleId") Long scheduleId,
            @RequestParam("classDate") String classDate)
    {
        return success(signInService.selectDetail(scheduleId, classDate));
    }

    /**
     * 确认复核（一节课需2位管理员；同管理员幂等，仅更新图片）
     */
    @PreAuthorize("@ss.hasRole('admin')")
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

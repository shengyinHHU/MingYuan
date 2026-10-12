package com.ruoyi.system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.service.ClassLeaveService;

@RestController
@RequestMapping("/miniapp")
public class MiniAppClassLeaveController extends BaseController {
    @Autowired private ClassLeaveService leaves;
    @GetMapping("/parent/leave/options") @PreAuthorize("@ss.hasRole('parent')")
    public AjaxResult options() { return success(leaves.options()); }
    @GetMapping("/parent/leave/lessons") @PreAuthorize("@ss.hasRole('parent')")
    public AjaxResult lessons(@RequestParam Long enrollmentId) { return success(leaves.lessons(enrollmentId)); }
    @GetMapping("/parent/leave") @PreAuthorize("@ss.hasRole('parent')")
    public AjaxResult parentList(@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="1") int pageNum) { return success(leaves.list(false,state,pageNum)); }
    @GetMapping("/teacher/leave") @PreAuthorize("@ss.hasRole('teacher')")
    public AjaxResult teacherList(@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="1") int pageNum) { return success(leaves.list(true,state,pageNum)); }
    @GetMapping("/parent/leave/{id}") @PreAuthorize("@ss.hasRole('parent')")
    public AjaxResult parentDetail(@PathVariable Long id) { return success(leaves.detail(id,false)); }
    @GetMapping("/teacher/leave/{id}") @PreAuthorize("@ss.hasRole('teacher')")
    public AjaxResult teacherDetail(@PathVariable Long id) { return success(leaves.detail(id,true)); }
    @PostMapping("/parent/leave") @PreAuthorize("@ss.hasRole('parent')") @Log(title="班课请假申请",businessType=BusinessType.INSERT)
    public AjaxResult apply(@RequestBody ClassLeaveService.Apply body) { return success(leaves.apply(body)); }
    @PostMapping("/parent/leave/{id}/withdraw") @PreAuthorize("@ss.hasRole('parent')") @Log(title="撤回班课请假",businessType=BusinessType.UPDATE)
    public AjaxResult withdraw(@PathVariable Long id,@RequestBody ClassLeaveService.Decision body) {
        if(body == null) throw new com.ruoyi.common.exception.ServiceException("请刷新后操作");
        body.action="WITHDRAW"; leaves.decide(id,body,false); return success();
    }
    @PostMapping("/teacher/leave/{id}/review") @PreAuthorize("@ss.hasRole('teacher')") @Log(title="审核班课请假",businessType=BusinessType.UPDATE)
    public AjaxResult review(@PathVariable Long id,@RequestBody ClassLeaveService.Decision body) { leaves.decide(id,body,true); return success(); }
}

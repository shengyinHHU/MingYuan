package com.ruoyi.system.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.shop.ShopRepository;

/** 后台教师资料配置；不接受小程序教师自行修改。 */
@RestController
@RequestMapping("/system/teacherProfile")
@PreAuthorize("@ss.hasPermi('system:user:edit')")
public class TeacherProfileController extends BaseController {
    @Autowired private ISysUserService users;
    @Autowired private ShopRepository db;
    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable Long id) {
        users.checkUserDataScope(id);
        return success(db.one("SELECT teacher_subject,teacher_level FROM sys_user WHERE user_id=? AND del_flag='0'",id));
    }
    public static class Profile { public String teacherSubject; public String teacherLevel; }
    @PutMapping("/{id}")
    @Log(title="教师授课资料",businessType=BusinessType.UPDATE)
    @Transactional(rollbackFor=Exception.class)
    public AjaxResult save(@PathVariable Long id,@RequestBody Profile p) {
        users.checkUserDataScope(id);
        if (p.teacherSubject == null || p.teacherSubject.isBlank() || p.teacherSubject.length()>20 || !List.of("elite","senior").contains(p.teacherLevel == null ? "" : p.teacherLevel)) throw new ServiceException("请选择学科和教师等级");
        db.one("SELECT user_id FROM sys_user WHERE user_id=? AND del_flag='0' FOR UPDATE",id);
        if (db.rows("SELECT ur.user_id FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=? AND r.role_key='teacher' AND r.status='0' AND r.del_flag='0'",id).isEmpty()) throw new ServiceException("此账号没有教师角色");
        if (db.rows("SELECT dict_code FROM sys_dict_data WHERE dict_type='edu_subject' AND dict_label=? AND status='0'",p.teacherSubject).isEmpty()) throw new ServiceException("请选择后台启用的授课学科");
        db.update("sys_user","user_id",id,Map.of("teacher_subject",p.teacherSubject,"teacher_level",p.teacherLevel,"update_by",getUsername(),"update_time",java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))));
        return success();
    }
}

package com.ruoyi.system.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.EduClassroom;
import com.ruoyi.system.domain.EduCourseSchedule;
import com.ruoyi.system.service.IEduClassroomService;
import com.ruoyi.system.service.IEduCourseScheduleService;
import com.ruoyi.system.service.ISysDictDataService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 可视化课表 Controller（矩阵式：纵向教室 × 横向时段）
 * 与 EduCourseScheduleController 共用同一张 edu_course_schedule 表；
 * 此处负责：维度数据（学期/期次/时段/教室/教师/字典）、网格批量查询、以及带角色校验的 CRUD 封装。
 */
@RestController
@RequestMapping("/system/courseTimetable")
public class EduTimetableController extends BaseController
{
    @Autowired
    private IEduCourseScheduleService scheduleService;

    @Autowired
    private IEduClassroomService classroomService;

    @Autowired
    private ISysDictDataService dictDataService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

    /**
     * 维度数据：返回页面初始化需要的下拉框 + 固定时段 + 校区分组教室
     * 菜单权限：system:courseschedule:list（与列表页共用同一权限标识，减少额外菜单配置）
     * 同时允许 teacher 角色直接调用（hasAnyRoles）
     */
    @PreAuthorize("@ss.hasPermi('system:courseschedule:list') or @ss.hasAnyPermi('system:schedule:list,system:schedule:add,system:schedule:edit') or @ss.hasAnyRoles('admin,teacher')")
    @GetMapping("/dimensions")
    public AjaxResult dimensions()
    {
        Map<String, Object> data = new HashMap<>();

        // 1. 学期字典
        data.put("terms",       dictLabelValueList("edu_term"));
        // 2. 期次字典
        data.put("periods",     dictLabelValueList("edu_term_period"));
        // 3. 时段字典（Excel 对齐的 6 个固定时段）
        data.put("timeSlots",   dictLabelValueList("edu_time_slot"));
        // 4. 班型字典
        data.put("classTypes",  dictLabelValueList("edu_class_type"));
        // 5. 年级字典
        data.put("grades",      dictLabelValueList("edu_grade"));
        // 6. 学科字典
        data.put("subjects",    dictLabelValueList("edu_subject"));

        // 7. 教室列表（按校区分组），仅包含状态正常的教室
        EduClassroom q = new EduClassroom();
        q.setStatus("0");
        List<EduClassroom> allClassrooms = classroomService.selectEduClassroomList(q);
        Map<String, List<EduClassroom>> grouped = allClassrooms.stream()
                .collect(Collectors.groupingBy(
                        c -> StringUtils.isEmpty(c.getCampusName()) ? "未分配校区" : c.getCampusName(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        data.put("classrooms", grouped);
        data.put("classroomList", allClassrooms);

        // 8. 教师下拉：role_key = teacher 的启用用户
        // selectUserList 不联角色表，getRoles() 恒为 null，导致原过滤全部失效、教师下拉为空；
        // 改为：先查 teacher 角色的 roleId，再用 selectAllocatedList 查该角色下的用户（联 sys_user_role/sys_role）
        List<Map<String, Object>> teachers = new ArrayList<>();
        Long teacherRoleId = roleService.selectRoleList(new SysRole()).stream()
                .filter(r -> "teacher".equals(r.getRoleKey()))
                .map(SysRole::getRoleId)
                .findFirst().orElse(null);
        if (teacherRoleId != null)
        {
            SysUser teacherQuery = new SysUser();
            teacherQuery.setRoleId(teacherRoleId);
            List<SysUser> teacherUsers = userService.selectAllocatedList(teacherQuery);
            teachers = teacherUsers.stream()
                    .filter(u -> "0".equals(u.getStatus()))
                    .sorted(Comparator.comparing(SysUser::getNickName, Comparator.nullsLast(String::compareTo)))
                    .map(u -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("userId", u.getUserId());
                        m.put("nickName", u.getNickName());
                        m.put("userName", u.getUserName());
                        return m;
                    })
                    .collect(Collectors.toList());
        }
        data.put("teachers", teachers);

        // 9. 当前年份、当前角色是否 admin、当前用户昵称（给前端做默认值）
        data.put("currentYear", Calendar.getInstance().get(Calendar.YEAR));
        data.put("isAdmin", SecurityUtils.isAdmin() || SecurityUtils.hasRole("admin"));
        data.put("isTeacher", SecurityUtils.hasRole("teacher"));
        data.put("currentUserNick", SecurityUtils.getLoginUser().getUser().getNickName());
        data.put("currentUserId", SecurityUtils.getUserId());

        return success(data);
    }

    /**
     * 网格查询：按 年+学期+期次 取出所有排课，前端按 classroomId + timeSlot 自行放单元格
     *
     * 请求参数：
     *   courseYear   (必)  Integer
     *   termName     (必)  String  暑假/秋季/春季
     *   periodName   (必)  String  一期/二期/三期
     *   campusName   (可选) String  校区（万达/百家湖），传空不过滤
     *   gradeName    (可选)
     *   subjectName  (可选)
     *   teacherName  (可选)
     *   classType    (可选)
     *   classroomId  (可选)
     */
    @PreAuthorize("@ss.hasPermi('system:courseschedule:list') or @ss.hasAnyRoles('admin,teacher')")
    @GetMapping("/grid")
    public AjaxResult grid(
            @RequestParam(value = "courseYear") Integer courseYear,
            @RequestParam(value = "termName")   String termName,
            @RequestParam(value = "periodName") String periodName,
            @RequestParam(value = "campusName",  required = false) String campusName,
            @RequestParam(value = "gradeName",   required = false) String gradeName,
            @RequestParam(value = "subjectName", required = false) String subjectName,
            @RequestParam(value = "teacherName", required = false) String teacherName,
            @RequestParam(value = "classType",   required = false) String classType,
            @RequestParam(value = "classroomId", required = false) Long classroomId)
    {
        EduCourseSchedule q = new EduCourseSchedule();
        q.setCourseYear(courseYear.longValue());
        q.setTermName(termName);
        q.setPeriodName(periodName);
        // remark 字段在 XML 里被借来当 campusName 条件（不入库，仅用在联表教室的 and c.campus_name = #{remark}）
        q.setRemark(StringUtils.trimToNull(campusName));
        q.setGradeName(gradeName);
        q.setSubjectName(subjectName);
        q.setTeacherName(teacherName);
        q.setClassType(classType);
        q.setClassroomId(classroomId);

        List<EduCourseSchedule> rows = scheduleService.selectTimetableGrid(q);
        Map<String, Object> data = new HashMap<>();
        // 前端用 "classroomId@timeSlot" 做 key 快速索引
        Map<String, EduCourseSchedule> map = new LinkedHashMap<>();
        for (EduCourseSchedule s : rows) {
            if (s.getClassroomId() != null && StringUtils.isNotEmpty(s.getTimeSlot())) {
                map.put(s.getClassroomId() + "@" + s.getTimeSlot(), s);
            }
        }
        data.put("rows", rows);
        data.put("cellMap", map);
        return success(data);
    }

    /**
     * 单条排课详情（给单元格点击"编辑"弹窗回填）
     */
    @PreAuthorize("@ss.hasPermi('system:courseschedule:query') or @ss.hasAnyRoles('admin,teacher')")
    @GetMapping("/{scheduleId}")
    public AjaxResult getInfo(@PathVariable Long scheduleId)
    {
        return success(scheduleService.selectEduCourseScheduleByScheduleId(scheduleId));
    }

    /**
     * 新增排课
     * 权限：admin 任意新增；teacher 新增时，teacherName 必须等于当前用户 nickName，否则拒绝
     */
    @PreAuthorize("@ss.hasPermi('system:courseschedule:add') or @ss.hasAnyRoles('admin,teacher')")
    @Log(title = "可视化课表-新增", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody EduCourseSchedule schedule)
    {
        fillBaseForCreate(schedule);
        checkEditablePermission(schedule, null, true);
        int rows = scheduleService.insertEduCourseSchedule(schedule);
        if (rows == 0) {
            throw new ServiceException("新增排课失败，请检查参数或唯一性约束（同教室+同学期+同期次+同时段已排课）");
        }
        return success(schedule.getScheduleId());
    }

    /**
     * 修改排课
     * 权限：admin 任意改；teacher 仅能改自己 teacher_id 对应的记录，且 teacherName 不能改成别人
     */
    @PreAuthorize("@ss.hasPermi('system:courseschedule:edit') or @ss.hasAnyRoles('admin,teacher')")
    @Log(title = "可视化课表-修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody EduCourseSchedule schedule)
    {
        EduCourseSchedule old = scheduleService.selectEduCourseScheduleByScheduleId(schedule.getScheduleId());
        if (old == null) {
            return error("排课不存在，可能已被删除");
        }
        checkEditablePermission(schedule, old, false);
        schedule.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(scheduleService.updateEduCourseSchedule(schedule));
    }

    /**
     * 删除排课
     * 权限：admin 任意删；teacher 仅能删自己 create_by 的记录
     */
    @PreAuthorize("@ss.hasPermi('system:courseschedule:remove') or @ss.hasAnyRoles('admin,teacher')")
    @Log(title = "可视化课表-删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{scheduleIds}")
    public AjaxResult remove(@PathVariable Long[] scheduleIds)
    {
        if (SecurityUtils.isAdmin() || SecurityUtils.hasPermi(Constants.ALL_PERMISSION)) {
            return toAjax(scheduleService.deleteEduCourseScheduleByScheduleIds(scheduleIds));
        }
        // teacher：逐条校验 create_by
        List<Long> allowed = new ArrayList<>();
        for (Long id : scheduleIds) {
            EduCourseSchedule old = scheduleService.selectEduCourseScheduleByScheduleId(id);
            if (old != null && Objects.equals(SecurityUtils.getUserId(), old.getTeacherId())) {
                allowed.add(id);
            } else if (old == null) {
                // 不存在跳过
            } else {
                throw new ServiceException("无权删除其他教师的排课：" + old.getScheduleId());
            }
        }
        if (allowed.isEmpty()) return success(0);
        return toAjax(scheduleService.deleteEduCourseScheduleByScheduleIds(allowed.toArray(new Long[0])));
    }

    // =================================================================
    // 内部工具方法
    // =================================================================

    /** 字典 → [{label, value}] 列表 */
    private List<Map<String, String>> dictLabelValueList(String dictType)
    {
        SysDictData q = new SysDictData();
        q.setDictType(dictType);
        q.setStatus("0");
        return dictDataService.selectDictDataList(q).stream()
                .sorted(Comparator.comparing(SysDictData::getDictSort))
                .map(d -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("label", d.getDictLabel());
                    m.put("value", Set.of("edu_grade", "edu_subject", "edu_class_type").contains(dictType) ? d.getDictLabel() : d.getDictValue());
                    m.put("isDefault", d.getIsDefault());
                    return m;
                })
                .collect(Collectors.toList());
    }

    /** 新增时填充 schedule_code / del_flag / status / recruit_status 默认值 */
    private void fillBaseForCreate(EduCourseSchedule s)
    {
        if (StringUtils.isEmpty(s.getScheduleCode())) {
            s.setScheduleCode("SCH" + System.currentTimeMillis() + (int)(Math.random() * 100));
        }
        if (StringUtils.isEmpty(s.getDelFlag())) s.setDelFlag("0");
        if (StringUtils.isEmpty(s.getStatus()))   s.setStatus("0");
        if (StringUtils.isEmpty(s.getRecruitStatus())) s.setRecruitStatus("0");
        if (s.getEnrolledCount() == null) s.setEnrolledCount(0L);
        if (s.getCourseYear() == null) {
            s.setCourseYear((long) Calendar.getInstance().get(Calendar.YEAR));
        }
        // create_by 自动赋值
        if (StringUtils.isEmpty(s.getCreateBy())) {
            s.setCreateBy(SecurityUtils.getUsername());
        }
    }

    /**
     * 新增/修改前检查 teacher 角色是否越权
     * @param input  本次提交的排课对象
     * @param old    数据库里已有对象（新增时为 null）
     * @param isNew  true=新增 / false=修改
     */
    private void checkEditablePermission(EduCourseSchedule input, EduCourseSchedule old, boolean isNew)
    {
        // admin 一律放行
        if (SecurityUtils.isAdmin() || SecurityUtils.hasRole("admin")) return;
        // 有通配权限也放行
        if (SecurityUtils.hasPermi(Constants.ALL_PERMISSION)) return;

        if (!SecurityUtils.hasRole("teacher")) {
            throw new ServiceException("当前用户不具备排课编辑权限");
        }

        if (!isNew && !Objects.equals(old.getTeacherId(), SecurityUtils.getUserId())) {
            throw new ServiceException("教师仅能修改本人负责的排课");
        }
        if (input.getTeacherId() != null && !Objects.equals(input.getTeacherId(), SecurityUtils.getUserId())) {
            throw new ServiceException("教师仅能为自己排课");
        }
        input.setTeacherId(SecurityUtils.getUserId());
        input.setTeacherName(SecurityUtils.getLoginUser().getUser().getNickName());
    }
}

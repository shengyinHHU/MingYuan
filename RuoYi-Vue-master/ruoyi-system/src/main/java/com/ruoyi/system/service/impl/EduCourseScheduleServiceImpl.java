package com.ruoyi.system.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.EduCourseSchedule;
import com.ruoyi.system.mapper.EduCourseScheduleMapper;
import com.ruoyi.system.mapper.MiniAppParentMapper;
import com.ruoyi.system.service.IEduCourseScheduleService;
import com.ruoyi.system.service.ScheduleCalendar;
import com.ruoyi.system.shop.ShopRepository;

/** 若依与小程序共用排课 CRUD；归属、人数与历史约束由后端维护。 */
@Service
public class EduCourseScheduleServiceImpl implements IEduCourseScheduleService {
    @Autowired private EduCourseScheduleMapper eduCourseScheduleMapper;
    @Autowired private MiniAppParentMapper parentMapper;
    @Autowired private ShopRepository db;
    @Autowired private com.ruoyi.system.service.OneToOneEnrollmentService singleService;
    @Autowired private com.ruoyi.system.service.ScheduleAvailabilityService availability;

    private boolean teacherOnly() {
        return SecurityUtils.hasRole("teacher") && !SecurityUtils.hasRole("admin") && !SecurityUtils.isAdmin();
    }
    private void own(EduCourseSchedule schedule) {
        require(schedule != null, "排课不存在或已删除");
        if (teacherOnly()) require(Objects.equals(schedule.getTeacherId(), SecurityUtils.getUserId()), "无权操作其他教师的排课");
    }
    private void scope(EduCourseSchedule query) {
        if (teacherOnly()) query.getParams().put("teacherUserId", SecurityUtils.getUserId());
        else query.getParams().remove("teacherUserId");
    }
    @Override public EduCourseSchedule selectEduCourseScheduleByScheduleId(Long id) {
        EduCourseSchedule schedule = eduCourseScheduleMapper.selectEduCourseScheduleByScheduleId(id);
        own(schedule);
        return schedule;
    }
    @Override public List<EduCourseSchedule> selectEduCourseScheduleList(EduCourseSchedule query) {
        scope(query);
        return eduCourseScheduleMapper.selectEduCourseScheduleList(query);
    }
    @Override public List<EduCourseSchedule> selectTimetableGrid(EduCourseSchedule query) {
        scope(query);
        return eduCourseScheduleMapper.selectTimetableGrid(query);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public int insertEduCourseSchedule(EduCourseSchedule schedule) {
        require(schedule != null && schedule.getScheduleId() == null, "新增排课不能指定排课ID");
        if (teacherOnly()) require("2".equals(schedule.getClassMode()), "教师只能新增一对一课程");
        bindTeacher(schedule, null);
        if ("2".equals(schedule.getClassMode())) schedule.setSubjectName(singleService.subject(schedule.getTeacherId()));
        if (blank(schedule.getScheduleCode())) schedule.setScheduleCode("SCH" + UUID.randomUUID().toString().replace("-", "").substring(0, 28));
        if (schedule.getCourseYear() == null) schedule.setCourseYear((long) LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).getYear());
        if (blank(schedule.getClassMode())) schedule.setClassMode("1");
        if (blank(schedule.getStatus())) schedule.setStatus("0");
        if (blank(schedule.getRecruitStatus())) schedule.setRecruitStatus("0");
        schedule.setDelFlag("0");
        schedule.setEnrolledCount(0L);
        lockResources(List.of(schedule));
        validate(schedule);
        if ("2".equals(schedule.getClassMode())) { availability.validateSingle(schedule); schedule.setLessonLocation(""); }
        checkConflicts(schedule);
        schedule.setCreateBy(SecurityUtils.getUsername());
        schedule.setCreateTime(DateUtils.getNowDate());
        return eduCourseScheduleMapper.insertEduCourseSchedule(schedule);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public int updateEduCourseSchedule(EduCourseSchedule input) {
        require(input != null && input.getScheduleId() != null, "请选择排课");
        EduCourseSchedule before = selectEduCourseScheduleByScheduleId(input.getScheduleId());
        bindTeacher(input, before);
        if (input.getClassroomId() == null) input.setClassroomId(before.getClassroomId());
        lockResources(List.of(before, input));
        EduCourseSchedule current = eduCourseScheduleMapper.lockSchedule(input.getScheduleId());
        own(current);
        requireNoActiveEnrollments(current);
        require(Objects.equals(before.getTeacherId(), current.getTeacherId()) && Objects.equals(before.getClassroomId(), current.getClassroomId()), "排课已被其他人调整，请刷新后重试");
        require(blank(input.getScheduleCode()) || input.getScheduleCode().equals(current.getScheduleCode()), "排课编码不可修改");
        if (teacherOnly() && "2".equals(current.getClassMode())) {
            require(input.getClassMode() == null || "2".equals(input.getClassMode()), "不能把一对一改成班课");
            input.setSubjectName(current.getSubjectName());
        }
        EduCourseSchedule effective = merge(current, input);
        validate(effective);
        if ("2".equals(effective.getClassMode()) && (singleResourcesChanged(current,effective) || !"2".equals(current.getClassMode()) || (!"0".equals(current.getStatus()) && "0".equals(effective.getStatus())))) {
            availability.validateSingle(effective);
            input.setLessonLocation(""); effective.setLessonLocation("");
        }
        if ("2".equals(current.getClassMode()) && "0".equals(current.getStatus()) && "1".equals(effective.getStatus()))
            require(db.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND del_flag='0' AND enrollment_status IN ('0','1','待确认','已确认','报名成功') FOR UPDATE",current.getScheduleId()).isEmpty(),"仍有有效报名，请先处理报名后再撤回课程");
        if (hasAttendance(current.getScheduleId()) || !db.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND class_date IS NOT NULL FOR UPDATE", current.getScheduleId()).isEmpty()) {
            require(Objects.equals(current.getTeacherId(), effective.getTeacherId())
                && Objects.equals(current.getClassroomId(), effective.getClassroomId())
                && Objects.equals(current.getClassMode(), effective.getClassMode())
                && Objects.equals(current.getLessonLocation(), effective.getLessonLocation())
                && Objects.equals(current.getSubjectName(), effective.getSubjectName())
                && Objects.equals(current.getStartTime(), effective.getStartTime())
                && Objects.equals(current.getEndTime(), effective.getEndTime())
                && Objects.equals(ScheduleCalendar.date(current.getStartDate()), ScheduleCalendar.date(effective.getStartDate()))
                && Objects.equals(ScheduleCalendar.date(current.getEndDate()), ScheduleCalendar.date(effective.getEndDate()))
                && Objects.equals(current.getClassPattern(), effective.getClassPattern())
                && Objects.equals(current.getPeriodName(), effective.getPeriodName()),
                "已有签到历史，不能直接改变教师、教室、授课形式或排课时间；请使用调课管理");
        }
        checkConflicts(effective);
        // 不用前端旧值覆盖报名事务维护的人数；创建信息和删除标志也不能普通编辑。
        input.setEnrolledCount(null); input.setDelFlag(null); input.setScheduleCode(null);
        input.setCreateBy(null); input.setCreateTime(null);
        input.setUpdateBy(SecurityUtils.getUsername()); input.setUpdateTime(DateUtils.getNowDate());
        return eduCourseScheduleMapper.updateEduCourseSchedule(input);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public int deleteEduCourseScheduleByScheduleIds(Long[] ids) {
        require(ids != null && ids.length > 0 && Arrays.stream(ids).noneMatch(Objects::isNull), "请选择要删除的排课");
        List<EduCourseSchedule> courses = Arrays.stream(ids).distinct().sorted().map(this::selectEduCourseScheduleByScheduleId).toList();
        lockResources(courses);
        for (EduCourseSchedule course : courses) {
            EduCourseSchedule locked = eduCourseScheduleMapper.lockSchedule(course.getScheduleId());
            own(locked);
            requireNoActiveEnrollments(locked);
            require(Objects.equals(course.getClassroomId(), locked.getClassroomId()) && Objects.equals(course.getTeacherId(), locked.getTeacherId()), "排课已被其他人调整，请刷新后重试");
            require(referenceCount(course.getScheduleId()) == 0, "排课「" + course.getCourseClassName() + "」已有报名、考勤、调课或作业，不能删除，请改为停用");
        }
        return eduCourseScheduleMapper.deleteEduCourseScheduleByScheduleIds(courses.stream().map(EduCourseSchedule::getScheduleId).toArray(Long[]::new));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public int deleteEduCourseScheduleByScheduleId(Long id) { return deleteEduCourseScheduleByScheduleIds(new Long[]{id}); }

    private void bindTeacher(EduCourseSchedule input, EduCourseSchedule current) {
        if (teacherOnly()) {
            require(input.getTeacherId() == null || input.getTeacherId().equals(SecurityUtils.getUserId()), "只能为当前教师排课");
            input.setTeacherId(SecurityUtils.getUserId());
        } else if (input.getTeacherId() == null && current != null) {
            input.setTeacherId(current.getTeacherId());
        }
        if (input.getTeacherId() == null) {
            require(current != null && Objects.equals(input.getTeacherName(), current.getTeacherName()), "请选择明确的教师账号，不能仅填写姓名");
            return; // 保留已有未绑定课程，不按同名猜测归属。
        }
        List<Map<String,Object>> teachers = db.rows("SELECT u.nick_name,u.user_name FROM sys_user u WHERE u.user_id=? AND u.status='0' AND u.del_flag='0' AND EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=u.user_id AND r.role_key='teacher' AND r.status='0' AND r.del_flag='0')", input.getTeacherId());
        require(!teachers.isEmpty(), "教师账号不存在、已停用或不具备教师角色");
        String name = String.valueOf(teachers.get(0).get("nickName"));
        input.setTeacherName(name.equals("null") || name.isBlank() ? String.valueOf(teachers.get(0).get("userName")) : name);
    }
    private void lockResources(List<EduCourseSchedule> courses) {
        availability.lockResources(courses);
    }
    private static boolean singleResourcesChanged(EduCourseSchedule a,EduCourseSchedule b) {
        return !Objects.equals(a.getClassroomId(),b.getClassroomId()) || !Objects.equals(a.getStartTime(),b.getStartTime()) || !Objects.equals(a.getEndTime(),b.getEndTime())
            || !Objects.equals(ScheduleCalendar.date(a.getStartDate()),ScheduleCalendar.date(b.getStartDate())) || !Objects.equals(ScheduleCalendar.date(a.getEndDate()),ScheduleCalendar.date(b.getEndDate()))
            || !Objects.equals(a.getClassPattern(),b.getClassPattern()) || !Objects.equals(a.getPeriodName(),b.getPeriodName()) || !Objects.equals(a.getTimeSlot(),b.getTimeSlot()) || !Objects.equals(a.getLessonLocation(),b.getLessonLocation());
    }
    private void validate(EduCourseSchedule s) {
        required(s.getScheduleCode(), 32, "排课编码"); required(s.getTermName(), 20, "学期"); required(s.getPeriodName(), 20, "期次或上课日");
        required(s.getTimeSlot(), 30, "时段"); required(s.getGradeName(), 20, "年级"); required(s.getSubjectName(), 20, "学科");
        required(s.getCourseClassName(), 100, "课程班名称");
        if ("1".equals(s.getClassMode())) require(s.getClassroomId() != null, "请选择教室");
        else if (s.getClassroomId() == null) required(s.getLessonLocation(), 255, "上课地点");
        require(s.getCourseYear() != null && s.getCourseYear() >= 2000 && s.getCourseYear() <= 2100, "课程年份应为2000至2100");
        require(s.getStartTime() != null && s.getEndTime() != null && s.getStartTime().isBefore(s.getEndTime()), "请填写正确的开始、结束时间，结束必须晚于开始");
        require(List.of("1", "2").contains(s.getClassMode()), "授课形式无效");
        require(List.of("0", "1").contains(s.getStatus()), "排课状态无效");
        require(List.of("0", "1", "2").contains(s.getRecruitStatus()), "招生状态无效");
        if ("2".equals(s.getClassMode())) require(Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() == 120 && s.getStartTime().getSecond() == s.getEndTime().getSecond(), "一对一每次固定2小时");
        require((s.getStartDate() == null) == (s.getEndDate() == null), "开课、结课日期需同时填写，或同时保留待排");
        if (s.getStartDate() != null) {
            LocalDate start = ScheduleCalendar.date(s.getStartDate()), end = ScheduleCalendar.date(s.getEndDate());
            require(start != null && end != null && !start.isAfter(end), "结课日期不能早于开课日期");
            require(java.time.temporal.ChronoUnit.DAYS.between(start, end) <= 3660, "排课跨度不能超过10年");
            require(List.of("WEEKLY", "DAILY_5_1").contains(s.getClassPattern() == null ? "" : s.getClassPattern()), "请明确上课模式");
            if ("WEEKLY".equals(s.getClassPattern())) require(ScheduleCalendar.weekday(s.getPeriodName()) > 0, "每周排课需明确周一至周日的上课星期");
        }
        Map<String,Object> classroom = s.getClassroomId() == null ? Map.of("status","0") : db.one("SELECT status,capacity FROM edu_classroom WHERE classroom_id=?", s.getClassroomId());
        if ("0".equals(s.getStatus())) require("0".equals(classroom.get("status")), "所选教室已停用");
        long enrolled = s.getScheduleId() == null ? 0 : db.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND del_flag='0' AND class_date IS NULL AND enrollment_status NOT IN('2','3','已取消') FOR UPDATE", s.getScheduleId()).size();
        Number capacity = (Number) classroom.get("capacity");
        if ("0".equals(s.getStatus()) && "2".equals(s.getClassMode())) require(capacity == null || capacity.longValue() > 0, "教室至少需容纳1名学员");
        require(capacity == null || enrolled <= capacity.longValue(), "所选教室容量少于当前报名人数");
        if ("2".equals(s.getClassMode())) require(enrolled <= 1, "一对一课程不能保留多名有效报名学员");
    }
    private void checkConflicts(EduCourseSchedule input) {
        long id = input.getScheduleId() == null ? -1 : input.getScheduleId();
        long duplicates = db.jdbc().queryForObject("SELECT COUNT(*) FROM edu_course_schedule WHERE schedule_id<>? AND schedule_code=?", Long.class, id, input.getScheduleCode());
        require(duplicates == 0, "排课编码已存在");
        long cells = "2".equals(input.getClassMode()) ? 0 : db.jdbc().queryForObject("SELECT COUNT(*) FROM edu_course_schedule WHERE schedule_id<>? AND classroom_id=? AND course_year=? AND term_name=? AND period_name=? AND time_slot=? AND del_flag='0' AND class_mode<>'2'", Long.class,
            id, input.getClassroomId(), input.getCourseYear(), input.getTermName(), input.getPeriodName(), input.getTimeSlot());
        require(cells == 0, "该教室在同年、学期、期次、时段已有排课");
        availability.check(input);
    }
    private boolean hasAttendance(Long id) {
        return !db.rows("SELECT sign_in_id FROM edu_class_sign_in WHERE schedule_id=? FOR UPDATE", id).isEmpty()
            || !db.rows("SELECT attendance_id FROM edu_attendance WHERE schedule_id=? AND attendance_status='1' FOR UPDATE", id).isEmpty();
    }
    private void requireNoActiveEnrollments(EduCourseSchedule course) {
        // Check actual registrations after locking the schedule shared by enrollment transactions.
        require(db.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND del_flag='0'"
            + " AND COALESCE(enrollment_status,'') NOT IN ('2','3','已取消','已拒绝','老师已拒绝') FOR UPDATE", course.getScheduleId()).isEmpty(),
            "排课「" + course.getCourseClassName() + "」已有学员报名，禁止修改或删除。请先与学员沟通并处理报名，清空班次后再操作。");
    }
    private long referenceCount(Long id) {
        // 包含无外键但引用 schedule_id 的历史表，例如签到、调课和作业。
        long count = 0;
        for (Map<String,Object> row : db.rows("SELECT TABLE_NAME AS table_name FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='schedule_id' AND table_name<>'edu_course_schedule'")) {
            String table = String.valueOf(row.get("tableName"));
            require(table.matches("[A-Za-z0-9_]+"), "关联表名异常");
            count += db.rows("SELECT schedule_id FROM `" + table + "` WHERE schedule_id=? FOR UPDATE", id).size();
        }
        return count;
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static void required(String value, int max, String label) { require(!blank(value) && value.length() <= max, label + "不能为空且不能超过" + max + "字"); }
    private static void require(boolean condition, String message) { if (!condition) throw new ServiceException(message); }
    private EduCourseSchedule merge(EduCourseSchedule old, EduCourseSchedule in) {
        EduCourseSchedule s = new EduCourseSchedule(); s.setScheduleId(old.getScheduleId()); s.setScheduleCode(old.getScheduleCode());
        s.setLessonLocation(in.getLessonLocation() == null ? old.getLessonLocation() : in.getLessonLocation());
        s.setTeacherId(in.getTeacherId()); s.setTeacherName(in.getTeacherName()); s.setClassroomId(in.getClassroomId());
        s.setCourseYear(in.getCourseYear() == null ? old.getCourseYear() : in.getCourseYear());
        s.setTermName(in.getTermName() == null ? old.getTermName() : in.getTermName()); s.setPeriodName(in.getPeriodName() == null ? old.getPeriodName() : in.getPeriodName());
        s.setTimeSlot(in.getTimeSlot() == null ? old.getTimeSlot() : in.getTimeSlot()); s.setGradeName(in.getGradeName() == null ? old.getGradeName() : in.getGradeName()); s.setSubjectName(in.getSubjectName() == null ? old.getSubjectName() : in.getSubjectName());
        s.setCourseClassName(in.getCourseClassName() == null ? old.getCourseClassName() : in.getCourseClassName());
        s.setStartTime(in.getStartTime() == null ? old.getStartTime() : in.getStartTime()); s.setEndTime(in.getEndTime() == null ? old.getEndTime() : in.getEndTime());
        s.setStartDate(in.getStartDate() == null ? old.getStartDate() : in.getStartDate()); s.setEndDate(in.getEndDate() == null ? old.getEndDate() : in.getEndDate());
        s.setClassPattern(in.getClassPattern() == null ? old.getClassPattern() : in.getClassPattern());
        s.setClassMode(blank(in.getClassMode()) ? (blank(old.getClassMode()) ? "1" : old.getClassMode()) : in.getClassMode());
        s.setStatus(in.getStatus() == null ? old.getStatus() : in.getStatus()); s.setRecruitStatus(in.getRecruitStatus() == null ? old.getRecruitStatus() : in.getRecruitStatus());
        return s;
    }
}

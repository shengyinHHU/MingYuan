package com.ruoyi.system.service;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.domain.model.MiniAppEnrollmentBody;
import com.ruoyi.system.domain.EduCourseSchedule;
import com.ruoyi.system.mapper.EduCourseScheduleMapper;
import com.ruoyi.system.mapper.MiniAppParentMapper;
import com.ruoyi.system.mapper.EduEnrollmentMapper;
import com.ruoyi.system.shop.ShopRepository;

/** 单次一对一是原报名表的按日期流程；支付/扣课不在此服务执行。 */
@Service
public class OneToOneEnrollmentService {
    @Autowired private ShopRepository db;
    @Autowired private EduCourseScheduleMapper schedules;
    @Autowired private MiniAppParentMapper parent;
    @Autowired private EduEnrollmentMapper enrollments;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ACTIVE = "('0','1','待确认','报名成功','已确认')";
    // 完成仅来源于该报名、班次和日期匹配且已复核的有效到课记录，不按时间推断。
    private static final String COMPLETED = "EXISTS(SELECT 1 FROM edu_class_sign_in r WHERE r.enrollment_id=e.enrollment_id AND r.schedule_id=e.schedule_id AND r.class_date=e.class_date AND r.del_flag='0' AND r.review_status='1' AND r.sign_status IN ('1','4','5')) AS lesson_completed";
    // 与 editable 的课前及历史限制保持一致，按服务器的上海时间计算，不依赖手机时钟。
    private static final String PROCESSABLE = "COALESCE(TIMESTAMP(e.class_date,s.start_time)>? AND e.enrollment_status IN"+ACTIVE+" AND NOT EXISTS(SELECT 1 FROM edu_class_sign_in r WHERE r.enrollment_id=e.enrollment_id) AND NOT EXISTS(SELECT 1 FROM edu_attendance a WHERE a.enrollment_id=e.enrollment_id AND a.attendance_status='1'),0) AS can_process";

    public Map<String,Object> profile(Long teacherId) {
        var rows = db.rows("SELECT u.user_id,u.nick_name,u.teacher_subject,u.teacher_level FROM sys_user u WHERE u.user_id=? AND u.status='0' AND u.del_flag='0' AND EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=u.user_id AND r.role_key='teacher' AND r.status='0' AND r.del_flag='0')", teacherId);
        check(!rows.isEmpty(), "教师账号不可用"); return rows.get(0);
    }
    public String subject(Long teacherId) {
        Object value = profile(teacherId).get("teacherSubject");
        check(value != null && !value.toString().isBlank(), "尚未配置授课学科，请联系后台管理员配置");
        return value.toString();
    }
    public boolean isSingle(Long scheduleId) {
        var schedule = schedules.selectEduCourseScheduleByScheduleId(scheduleId);
        return schedule != null && "2".equals(schedule.getClassMode());
    }
    public SortedSet<LocalDate> dates(EduCourseSchedule s) {
        return ScheduleCalendar.dates(ScheduleCalendar.date(s.getStartDate()), ScheduleCalendar.date(s.getEndDate()), s.getClassPattern(), s.getPeriodName(), parent.selectScheduleAdjustments(s.getScheduleId()));
    }
    private boolean future(EduCourseSchedule s, LocalDate date) {
        return s.getStartTime() != null && LocalDateTime.of(date,s.getStartTime()).isAfter(LocalDateTime.now(ZONE));
    }
    public List<Map<String,Object>> available() {
        var list = db.rows("SELECT s.schedule_id,s.teacher_id,COALESCE(NULLIF(TRIM(u.user_name),''),NULLIF(TRIM(u.nick_name),''),s.teacher_name) AS teacher_name,s.grade_name,s.subject_name,s.course_class_name,s.start_time,s.end_time,s.start_date,s.end_date,s.class_pattern,s.period_name,s.term_name,COALESCE(NULLIF(s.lesson_location,''),c.classroom_name) AS lesson_location FROM edu_course_schedule s JOIN sys_user u ON u.user_id=s.teacher_id LEFT JOIN edu_classroom c ON c.classroom_id=s.classroom_id WHERE s.class_mode='2' AND s.status='0' AND s.del_flag='0' AND s.recruit_status='0' AND u.status='0' AND u.del_flag='0' ORDER BY teacher_name,s.start_date,s.start_time,s.schedule_id");
        for (var item : list) {
            EduCourseSchedule s = schedules.selectEduCourseScheduleByScheduleId(id(item,"scheduleId"));
            var occupied = bookings(s.getScheduleId());
            boolean legacy = !db.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND class_date IS NULL AND del_flag='0' AND enrollment_status IN"+ACTIVE,s.getScheduleId()).isEmpty();
            List<Map<String,Object>> lessons = new ArrayList<>();
            for (LocalDate date : dates(s)) {
                var booking = occupied.stream().filter(e -> date.equals(ScheduleCalendar.date(e.get("classDate")))).findFirst().orElse(null);
                var lesson = new LinkedHashMap<String,Object>();
                lesson.put("classDate",date.toString()); lesson.put("canEnroll",future(s,date) && booking == null && !legacy);
                lesson.put("statusText", legacy ? "旧报名日期待核对" : !future(s,date) ? "已开始或已过期" : booking == null ? "可报名" : "已被报名");
                if (booking != null && SecurityUtils.getUserId().equals(id(booking,"parentId"))) {
                    lesson.put("enrollmentId",booking.get("enrollmentId")); lesson.put("statusText",status(booking));
                }
                lessons.add(lesson);
            }
            item.put("lessons",lessons);
        }
        return formatRows(list);
    }
    public List<Map<String,Object>> bookings(Long scheduleId) {
        return formatRows(db.rows("SELECT e.enrollment_id,e.parent_id,e.class_date,e.student_name,e.enrollment_status,e.cancel_request_status,"+COMPLETED+" FROM edu_enrollment e WHERE e.schedule_id=? AND e.class_date IS NOT NULL AND e.del_flag='0' AND e.enrollment_status IN"+ACTIVE+" ORDER BY e.class_date",scheduleId));
    }
    public void decorateTeacher(Map<String,Object> course) {
        // 调用方的课程列表已按当前教师ID隔离。
        if ("2".equals(String.valueOf(course.get("classMode")))) course.put("singleBookings",bookings(id(course,"scheduleId")));
    }
    @Transactional(rollbackFor=Exception.class)
    public int enroll(MiniAppEnrollmentBody body) {
        check(body != null && body.getScheduleId() != null && body.getClassDate() != null,"请选择具体上课日期");
        EduCourseSchedule s = schedules.lockSchedule(body.getScheduleId());
        check(s != null && "2".equals(s.getClassMode()) && "0".equals(s.getStatus()) && "0".equals(s.getRecruitStatus()),"课程未开放报名或已下架");
        profile(s.getTeacherId());
        check(db.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND class_date IS NULL AND del_flag='0' AND enrollment_status IN"+ACTIVE+" FOR UPDATE",s.getScheduleId()).isEmpty(),"此课程有旧报名日期待核对，不能猜测空闲名额");
        LocalDate date = ScheduleCalendar.date(body.getClassDate());
        check(date != null && dates(s).contains(date) && future(s,date),"该日期不可报名或课程已开始");
        String name = body.getStudentName() == null ? "" : body.getStudentName().trim();
        check(!name.isEmpty() && name.length() <= 30,"请填写30字以内的学生姓名");
        check(body.getContactPhone() == null || body.getContactPhone().length() <= 11,"联系电话过长");
        var active = db.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND class_date=? AND del_flag='0' AND enrollment_status IN"+ACTIVE+" FOR UPDATE",s.getScheduleId(),date);
        check(active.isEmpty(),"该日期已有人报名，请选择其他日期");
        String code = "ENR"+UUID.randomUUID().toString().replace("-", "").substring(0,28);
        var values = new LinkedHashMap<String,Object>();
        values.put("enrollment_code",code); values.put("schedule_id",s.getScheduleId()); values.put("parent_id",SecurityUtils.getUserId());
        values.put("class_date",date); values.put("student_name",name); values.put("student_phone",""); values.put("contact_phone",body.getContactPhone());
        values.put("enrollment_status","0"); values.put("pay_status","0"); values.put("status","0"); values.put("del_flag","0");
        values.put("create_by",SecurityUtils.getUsername()); values.put("create_time",LocalDateTime.now(ZONE));
        long enrollmentId = db.insert("edu_enrollment",values);
        parent.insertInitialAttendance("ATT"+UUID.randomUUID().toString().replace("-", "").substring(0,28),s.getScheduleId(),enrollmentId,SecurityUtils.getUserId(),name,SecurityUtils.getUsername());
        event(enrollmentId,"SUBMIT",null,"0",null);
        return 1;
    }
    private Map<String,Object> lock(Long enrollmentId, boolean teacher) {
        var before = db.one("SELECT schedule_id FROM edu_enrollment WHERE enrollment_id=?",enrollmentId);
        EduCourseSchedule s = schedules.lockSchedule(id(before,"scheduleId"));
        check(s != null && "2".equals(s.getClassMode()),"不是单次一对一课程");
        var e = db.one("SELECT * FROM edu_enrollment WHERE enrollment_id=? FOR UPDATE",enrollmentId);
        check(e.get("classDate") != null && "0".equals(e.get("delFlag")),"旧报名未指定单次日期，请联系管理员核对");
        check(SecurityUtils.getUserId().equals(teacher ? s.getTeacherId() : id(e,"parentId")),"无权操作其他人的报名");
        e.put("schedule",s); return e;
    }
    private void editable(Map<String,Object> e) {
        EduCourseSchedule s = (EduCourseSchedule)e.get("schedule");
        check(future(s,ScheduleCalendar.date(e.get("classDate"))),"课程已开始，课后处理规则尚未开放；历史记录保留");
        check(db.rows("SELECT sign_in_id FROM edu_class_sign_in WHERE enrollment_id=? FOR UPDATE",id(e,"enrollmentId")).isEmpty()
            && db.rows("SELECT attendance_id FROM edu_attendance WHERE enrollment_id=? AND attendance_status='1' FOR UPDATE",id(e,"enrollmentId")).isEmpty(),"已有上课历史，不能执行课前取消或拒绝");
    }
    @Transactional(rollbackFor=Exception.class)
    public int decide(Long enrollmentId, String action, String reason) {
        var e = lock(enrollmentId,true); String before = String.valueOf(e.get("enrollmentStatus"));
        check(List.of("CONFIRM","REJECT","APPROVE_CANCEL","REJECT_CANCEL").contains(action),"操作无效");
        if ("CONFIRM".equals(action) && "1".equals(before)) return 0;
        if ("REJECT".equals(action) && "3".equals(before)) return 0;
        if ("APPROVE_CANCEL".equals(action) && "2".equals(before) && "2".equals(e.get("cancelRequestStatus"))) return 0;
        if ("REJECT_CANCEL".equals(action) && "3".equals(e.get("cancelRequestStatus"))) return 0;
        editable(e);
        if (action.endsWith("CANCEL")) check("1".equals(e.get("cancelRequestStatus")),"没有待处理取消申请");
        else check("0".equals(before) && !"1".equals(e.get("cancelRequestStatus")),"报名状态已变化，请刷新；请先处理取消申请");
        String next = action.equals("CONFIRM") ? "1" : action.equals("REJECT") ? "3" : action.equals("APPROVE_CANCEL") ? "2" : before;
        var values = new LinkedHashMap<String,Object>(); values.put("enrollment_status",next);
        if (action.equals("CONFIRM")) {
            EduCourseSchedule s = (EduCourseSchedule)e.get("schedule"); check("0".equals(s.getStatus()) && "0".equals(s.getRecruitStatus()),"课程已下架，不能确认接课");
            values.put("teacher_confirm_by",SecurityUtils.getUserId()); values.put("teacher_confirm_time",LocalDateTime.now(ZONE));
        }
        if (action.endsWith("CANCEL")) {
            values.put("cancel_request_status",action.equals("APPROVE_CANCEL") ? "2" : "3");
            values.put("cancel_processed_by",SecurityUtils.getUserId()); values.put("cancel_processed_time",LocalDateTime.now(ZONE));
        }
        if (next.equals("2") || next.equals("3")) {
            values.put("cancel_reason",reason(reason)); values.put("cancel_time",LocalDateTime.now(ZONE));
            enrollments.invalidateUnusedAttendance(enrollmentId,SecurityUtils.getUsername());
        }
        values.put("update_by",SecurityUtils.getUsername()); values.put("update_time",LocalDateTime.now(ZONE));
        db.update("edu_enrollment","enrollment_id",enrollmentId,values); event(enrollmentId,action,before,next,reason);
        return 1;
    }
    @Transactional(rollbackFor=Exception.class)
    public int requestCancellation(Long enrollmentId, String reason) {
        var e = lock(enrollmentId,false); String before = String.valueOf(e.get("enrollmentStatus"));
        check(List.of("0","1").contains(before),"该报名已结束处理");
        if ("1".equals(e.get("cancelRequestStatus"))) return 0;
        editable(e); check(reason != null && !reason.isBlank(),"请填写取消原因");
        var values = new LinkedHashMap<String,Object>(); values.put("cancel_request_status","1"); values.put("cancel_request_reason",reason(reason));
        values.put("cancel_request_time",LocalDateTime.now(ZONE)); values.put("cancel_processed_by",null); values.put("cancel_processed_time",null);
        values.put("update_by",SecurityUtils.getUsername()); values.put("update_time",LocalDateTime.now(ZONE));
        db.update("edu_enrollment","enrollment_id",enrollmentId,values); event(enrollmentId,"REQUEST_CANCEL",before,before,reason); return 1;
    }
    public List<Map<String,Object>> transactions() {
        return formatRows(db.rows("SELECT e.enrollment_id,e.schedule_id,e.student_name,e.class_date,e.enrollment_status,e.cancel_request_status,e.cancel_request_reason,e.create_time,"+COMPLETED+","+PROCESSABLE+",s.course_class_name,s.start_time,s.end_time,COALESCE(NULLIF(s.lesson_location,''),c.classroom_name) AS lesson_location FROM edu_enrollment e JOIN edu_course_schedule s ON s.schedule_id=e.schedule_id LEFT JOIN edu_classroom c ON c.classroom_id=s.classroom_id WHERE s.teacher_id=? AND s.class_mode='2' AND e.class_date IS NOT NULL AND e.del_flag='0' ORDER BY (e.cancel_request_status='1' OR e.enrollment_status='0') DESC,e.class_date DESC,e.enrollment_id DESC",LocalDateTime.now(ZONE),SecurityUtils.getUserId()));
    }
    public List<Map<String,Object>> history(Long id,boolean teacher) {
        var e = db.one("SELECT e.parent_id,s.teacher_id FROM edu_enrollment e JOIN edu_course_schedule s ON s.schedule_id=e.schedule_id WHERE e.enrollment_id=?",id);
        check(SecurityUtils.getUserId().equals(id(e,teacher ? "teacherId" : "parentId")),"无权查看其他人的操作历史");
        return db.rows("SELECT action,actor_name,reason,previous_status,next_status,create_time FROM edu_enrollment_event WHERE enrollment_id=? ORDER BY event_id",id);
    }
    private void event(Long id,String action,String before,String after,String reason) {
        var values = new LinkedHashMap<String,Object>(); values.put("enrollment_id",id); values.put("action",action); values.put("actor_id",SecurityUtils.getUserId());
        values.put("actor_name",SecurityUtils.getUsername()); values.put("previous_status",before); values.put("next_status",after); values.put("reason",reason(reason));
        values.put("create_time",LocalDateTime.now(ZONE)); db.insert("edu_enrollment_event",values);
    }
    private static List<Map<String,Object>> formatRows(List<Map<String,Object>> rows) {
        for (var row : rows) for (var entry : row.entrySet()) {
            if (entry.getValue() instanceof java.sql.Date date) entry.setValue(date.toLocalDate().toString());
            else if (entry.getValue() instanceof java.sql.Time time) entry.setValue(time.toLocalTime().toString());
        }
        return rows;
    }
    private static String reason(String reason) { check(reason == null || reason.length() <= 255,"原因不能超过255字"); return reason == null ? null : reason.trim(); }
    public static String status(Map<String,Object> e) {
        String base = switch(String.valueOf(e.get("enrollmentStatus"))) {case "0" -> "待老师确认"; case "1" -> "已确认";case "2" -> "已取消";case "3" -> "老师已拒绝";default -> "待核对";};
        return base + ("1".equals(e.get("cancelRequestStatus")) ? " · 取消申请待处理" : "");
    }
    private static Long id(Map<String,Object> e,String key) { Object value=e.get(key); return value == null ? null : Long.valueOf(value.toString()); }
    private static void check(boolean ok,String message) { if (!ok) throw new ServiceException(message); }
}

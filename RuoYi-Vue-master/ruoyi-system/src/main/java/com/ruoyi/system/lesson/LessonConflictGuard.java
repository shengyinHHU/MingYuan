package com.ruoyi.system.lesson;

import static com.ruoyi.system.finance.FinanceRules.*;
import com.ruoyi.system.service.ScheduleCalendar;
import com.ruoyi.system.shop.ShopRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Shared by lesson and legacy writers. Call in a READ_COMMITTED transaction; acquire resources
 * before schedule/slot rows, then students, bills/packages/bookings. Conflict reads deliberately
 * do not lock unrelated course rows: resource/student locks serialize their participating writers. */
@Service
public class LessonConflictGuard {
  private final ShopRepository db;
  public LessonConflictGuard(ShopRepository db) {this.db=db;}
  private void transaction() {
    require(TransactionSynchronizationManager.isActualTransactionActive(),"冲突校验必须在事务内执行");
    require(Objects.equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),java.sql.Connection.TRANSACTION_READ_COMMITTED),"冲突写入口必须使用READ_COMMITTED事务");
  }
  public void lockResources(Collection<Long> teachers,Collection<Long> classrooms) {
    transaction();
    for(long teacher:new TreeSet<>(teachers)) db.one("SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE",teacher);
    for(long room:new TreeSet<>(classrooms)) db.one("SELECT classroom_id FROM edu_classroom WHERE classroom_id=? FOR UPDATE",room);
  }
  public Map<String,Object> lockStudent(long student,long parent) {
    transaction();
    var row=db.one("SELECT * FROM edu_student WHERE student_id=? AND parent_id=? FOR UPDATE",student,parent);
    require("0".equals(row.get("status"))&&"0".equals(row.get("delFlag")),"学员不存在或已停用");return row;
  }
  public void assertResourceAvailable(long teacher,long classroom,Timestamp start,Timestamp end,long excludedSlot,long excludedSchedule) {
    transaction();
    require(db.rows("SELECT slot_id FROM edu_one_to_one_slot WHERE slot_id<>? AND status='OPEN' AND (teacher_id=? OR classroom_id=?) AND start_time<? AND end_time>?",excludedSlot,teacher,classroom,end,start).isEmpty(),"教师或教室与开放时段冲突");
    // CLOSED slots may still have bookings. The immutable booking snapshot remains occupied.
    require(db.rows("SELECT booking_id FROM edu_one_to_one_booking WHERE slot_id<>? AND status IN ('BOOKED','COMPLETED') AND (teacher_id=? OR classroom_id=?) AND start_time_snapshot<? AND end_time_snapshot>?",excludedSlot,teacher,classroom,end,start).isEmpty(),"教师或教室与预约冲突");
    for(var course:db.rows("SELECT * FROM edu_course_schedule WHERE schedule_id<>? AND status='0' AND del_flag='0' AND (teacher_id=? OR classroom_id=?)",excludedSchedule,teacher,classroom))
      assertCourseDoesNotOverlap(course,start,end);
  }
  public void assertStudentAvailable(long student,Timestamp start,Timestamp end,long excludedBooking,long excludedSchedule) {
    transaction();
    require(db.rows("SELECT booking_id FROM edu_one_to_one_booking WHERE student_id=? AND booking_id<>? AND status IN ('BOOKED','COMPLETED') AND start_time_snapshot<? AND end_time_snapshot>?",student,excludedBooking,end,start).isEmpty(),"学员已有重叠预约");
    for(var course:db.rows("SELECT DISTINCT s.* FROM edu_course_schedule s JOIN edu_enrollment e ON e.schedule_id=s.schedule_id WHERE e.student_id=? AND e.bill_type='COURSE' AND e.status='0' AND e.del_flag='0' AND e.enrollment_status NOT IN('2','已取消') AND s.status='0' AND s.del_flag='0' AND s.schedule_id<>?",student,excludedSchedule))
      assertCourseDoesNotOverlap(course,start,end);
  }
  /** Expand effective legacy course dates (including adjustments) and validate against new slots.
   * Caller locks old+new resources before its schedule row. Use for CRUD, batch and adjustments. */
  public void assertCourseAvailable(Map<String,Object> course,List<Map<String,Object>> adjustments) {
    transaction();long schedule=course.get("scheduleId")==null?-1:id(course.get("scheduleId"));
    if(!"0".equals(text(course.get("status")))||!"0".equals(text(course.get("delFlag"))))return;
    Long teacher=course.get("teacherId")==null?null:id(course.get("teacherId"));long room=id(course.get("classroomId"));
    var intervals=intervals(course,adjustments);
    for(Timestamp[] interval:intervals) {
      require(db.rows("SELECT slot_id FROM edu_one_to_one_slot WHERE status='OPEN' AND (teacher_id=? OR classroom_id=?) AND start_time<? AND end_time>?",teacher,room,interval[1],interval[0]).isEmpty(),"普通课程与开放时段冲突");
      require(db.rows("SELECT booking_id FROM edu_one_to_one_booking WHERE status IN ('BOOKED','COMPLETED') AND (teacher_id=? OR classroom_id=?) AND start_time_snapshot<? AND end_time_snapshot>?",teacher,room,interval[1],interval[0]).isEmpty(),"普通课程与预约冲突");
      for(var enrollment:db.rows("SELECT student_id FROM edu_enrollment WHERE schedule_id=? AND bill_type='COURSE' AND student_id IS NOT NULL AND status='0' AND del_flag='0' AND enrollment_status NOT IN('2','已取消')",schedule))
        assertStudentAvailable(id(enrollment.get("studentId")),interval[0],interval[1],-1,schedule);
    }
  }
  /** New ordinary enrollment: lock its student after resources/schedule, then call this. */
  public void assertStudentCourseAvailable(long student,Map<String,Object> course,List<Map<String,Object>> adjustments) {
    transaction();long schedule=id(course.get("scheduleId"));
    for(Timestamp[] interval:intervals(course,adjustments))assertStudentAvailable(student,interval[0],interval[1],-1,schedule);
  }
  private void assertCourseDoesNotOverlap(Map<String,Object> course,Timestamp start,Timestamp end) {
    var adjustments=db.rows("SELECT original_date,adjusted_date FROM edu_schedule_adjustment WHERE schedule_id=?",course.get("scheduleId"));
    for(Timestamp[] interval:intervals(course,adjustments))
      require(!start.before(interval[1])||!interval[0].before(end),"与普通课程时间冲突，请调整时段");
  }
  public List<Timestamp[]> intervals(Map<String,Object> course,List<Map<String,Object>> adjustments) {
    LocalDate first=ScheduleCalendar.date(course.get("startDate")),last=ScheduleCalendar.date(course.get("endDate"));
    String pattern=text(course.get("classPattern")),period=text(course.get("periodName"));
    boolean valid=first!=null&&last!=null&&!first.isAfter(last)&&java.time.temporal.ChronoUnit.DAYS.between(first,last)<=3660;
    valid&="DAILY_5_1".equals(pattern)||("WEEKLY".equals(pattern)&&ScheduleCalendar.weekday(period)>0)||(pattern.isBlank()&&Set.of("周六","周日").contains(period));
    require(valid,"历史排课日期或模式不明确，请先维护排课："+text(course.get("scheduleId")));
    LocalTime from,to;
    try {from=LocalTime.parse(text(course.get("startTime")));to=LocalTime.parse(text(course.get("endTime")));}
    catch(RuntimeException e){throw new com.ruoyi.common.exception.ServiceException("历史排课时间不明确，请先维护排课："+text(course.get("scheduleId")));}
    require(from.isBefore(to),"历史排课时间无效，请先维护排课："+text(course.get("scheduleId")));
    Set<LocalDate> originals=new HashSet<>();
    for(var a:adjustments)require(ScheduleCalendar.date(a.get("originalDate"))!=null&&(a.get("adjustedDate")==null||ScheduleCalendar.date(a.get("adjustedDate"))!=null)&&originals.add(ScheduleCalendar.date(a.get("originalDate"))),"调课日期不明确，请先维护调课");
    String effective=pattern.isBlank()?"WEEKLY":pattern;
    List<Timestamp[]> result=new ArrayList<>();
    for(LocalDate date:ScheduleCalendar.dates(first,last,effective,period,adjustments))result.add(new Timestamp[]{Timestamp.valueOf(date.atTime(from)),Timestamp.valueOf(date.atTime(to))});
    return result;
  }
}

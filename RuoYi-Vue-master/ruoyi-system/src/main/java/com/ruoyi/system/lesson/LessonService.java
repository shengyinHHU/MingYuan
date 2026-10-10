package com.ruoyi.system.lesson;

import static com.ruoyi.system.finance.FinanceRules.*;
import com.ruoyi.system.finance.CouponService;
import com.ruoyi.system.shop.ShopRepository;
import com.ruoyi.common.exception.ServiceException;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LessonService {
  private final ShopRepository db;
  private final LessonConflictGuard guard;
  private final LessonFinance finance;
  private final boolean mockEnabled;
  private final TransactionTemplate transactions;
  @Autowired public LessonService(ShopRepository db,LessonConflictGuard guard,Environment env) {
    this(db,guard,mockAllowed(env.getActiveProfiles(),env.getProperty("payment.mode","disabled")));
  }
  public LessonService(ShopRepository db,LessonConflictGuard guard,boolean mockEnabled) {
    this.db=db;this.guard=guard;this.mockEnabled=mockEnabled;this.finance=new LessonFinance(db);
    transactions=new TransactionTemplate(Objects.requireNonNull(db.transactions().getTransactionManager()));
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }
  private <T> T tx(Supplier<T> action) {
    try{return transactions.execute(s->action.get());}
    catch(PessimisticLockingFailureException e){throw new ServiceException("操作繁忙，请按原请求编号查询并重试");}
  }
  private void role(long actor,String role) {
    require(!db.rows("SELECT u.user_id FROM sys_user u WHERE u.user_id=? AND u.status='0' AND u.del_flag='0' AND EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=u.user_id AND r.role_key=? AND r.status='0' AND r.del_flag='0')",actor,role).isEmpty(),"FORBIDDEN: "+role+"权限不足");
  }
  private void actor(long actor,boolean admin) {role(actor,admin?"admin":"parent");}
  private String required(Object value,int max,String field) {String s=text(value);require(!s.isBlank()&&s.length()<=max,field+"不能为空或超过"+max+"字");return s;}
  private String status(Map<String,Object> body) {String s=body.containsKey("status")?str(body,"status"):"0";require(Set.of("0","1").contains(s),"状态无效");return s;}
  private String dictionary(String type,Object value) {
    String code=required(value,30,"字典值");var entries=db.rows("SELECT dict_label FROM sys_dict_data WHERE dict_type=? AND dict_value=? AND status='0'",type,code);
    require(entries.size()==1,"学科或年级字典无效，请选择有效字典项");return text(entries.get(0).get("dictLabel"));
  }
  private Map<String,Object> audit(long actor) {return values("update_by",String.valueOf(actor),"update_time",now());}
  private long save(String table,String idColumn,long record,Map<String,Object> values,long actor) {
    values.putAll(audit(actor));if(record==0){values.put("create_by",String.valueOf(actor));return db.insert(table,values);}db.update(table,idColumn,record,values);return record;
  }
  private Map<String,Object> output(Map<String,Object> source) {var row=dto(source);if(row.get("cancelledBy")!=null)row.put("cancelledBy",text(row.get("cancelledBy")));return row;}
  private Map<String,Object> row(String table,String column,long id) {return output(db.one("SELECT * FROM "+table+" WHERE "+column+"=?",id));}
  @SuppressWarnings("unchecked")
  private Map<String,Object> page(String table,String where,List<Object> args,Map<String,String> q,Map<String,String> filters) {
    for(var filter:filters.entrySet())if(!str(q,filter.getKey()).isBlank()){where+=" AND "+filter.getValue()+"=?";args.add(q.get(filter.getKey()));}
    boolean bookings=table.equals("edu_one_to_one_booking");String from=bookings?table+" b JOIN edu_student child ON child.student_id=b.student_id":table;
    var page=new LinkedHashMap<>(CouponService.page(db,(bookings?"SELECT b.*,child.student_name FROM ":"SELECT * FROM ")+from+where+" ORDER BY "+(bookings?"b.":"")+"create_time DESC","SELECT COUNT(*) FROM "+from+where,args,q));
    page.put("rows",((List<Map<String,Object>>)page.get("rows")).stream().map(this::output).toList());return page;
  }
  public Map<String,Object> students(long actor,boolean admin,Map<String,String> q) {
    actor(actor,admin);return page("edu_student"," WHERE del_flag='0'"+(admin?"":" AND parent_id=?"),new ArrayList<>(admin?List.of():List.of(actor)),q,Map.of("parentId","parent_id","studentId","student_id","status","status"));
  }
  public Map<String,Object> saveStudent(long actor,boolean admin,Map<String,Object> body) {
    actor(actor,admin);return tx(()->{
      long student=body.get("studentId")==null?0:id(body.get("studentId"));
      long parent=admin?id(body.get("parentId")):actor;role(parent,"parent");
      if(student!=0){var old=db.one("SELECT * FROM edu_student WHERE student_id=? AND parent_id=? AND del_flag='0' FOR UPDATE",student,parent);require(id(old.get("parentId"))==parent,"不可变更学员归属");}
      String state=status(body);
      if(student!=0&&"1".equals(state)){
        require(db.rows("SELECT enrollment_id FROM edu_enrollment WHERE student_id=? AND bill_type='COURSE' AND status='0' AND del_flag='0' AND enrollment_status NOT IN('2','已取消')",student).isEmpty(),"学员有有效报名，不能停用");
        require(db.rows("SELECT booking_id FROM edu_one_to_one_booking WHERE student_id=? AND status='BOOKED' AND end_time_snapshot>?",student,now()).isEmpty(),"学员有未结束预约，不能停用");
        require(db.rows("SELECT student_package_id FROM edu_student_package WHERE student_id=? AND (available_units>0 OR status IN('PENDING_PAYMENT','REFUND_FROZEN'))",student).isEmpty(),"学员有课包余额或待处理课包，不能停用");
      }
      String phone=str(body,"studentPhone"),grade=str(body,"gradeCode");require(phone.isEmpty()||phone.matches("1[0-9]{10}"),"学员电话无效");if(!grade.isBlank())dictionary("edu_grade",grade);
      student=save("edu_student","student_id",student,values("parent_id",parent,"student_name",required(body.get("studentName"),30,"学员姓名"),"student_phone",phone,"grade_code",grade,"status",state),actor);
      return row("edu_student","student_id",student);
    });
  }
  public Map<String,Object> linkEnrollment(long actor,Map<String,Object> body) {
    role(actor,"admin");String why=reason(body,"reason",500);return tx(()->{
      long enrollment=id(body.get("enrollmentId")),student=id(body.get("studentId"));
      var before=db.one("SELECT * FROM edu_enrollment WHERE enrollment_id=?",enrollment);require("COURSE".equals(before.get("billType")),"只能关联普通历史报名");
      var course=db.one("SELECT * FROM edu_course_schedule WHERE schedule_id=?",before.get("scheduleId"));
      guard.lockResources(course.get("teacherId")==null?List.of():List.of(id(course.get("teacherId"))),List.of(id(course.get("classroomId"))));
      var current=db.one("SELECT * FROM edu_course_schedule WHERE schedule_id=? FOR UPDATE",course.get("scheduleId"));
      require(Objects.equals(course.get("teacherId"),current.get("teacherId"))&&Objects.equals(course.get("classroomId"),current.get("classroomId")),"排课已调整，请刷新后重试");
      guard.lockStudent(student,id(before.get("parentId")));
      var bill=db.one("SELECT * FROM edu_enrollment WHERE enrollment_id=? FOR UPDATE",enrollment);
      if(bill.get("studentId")!=null){require(id(bill.get("studentId"))==student,"历史报名已关联，不能替换学员");return output(bill);}
      if(!Set.of("2","已取消").contains(bill.get("enrollmentStatus"))&&"0".equals(current.get("status"))) {
        var adjustments=db.rows("SELECT original_date,adjusted_date FROM edu_schedule_adjustment WHERE schedule_id=?",current.get("scheduleId"));
        // Only future course intervals can introduce a new active conflict through a historical link.
        for(Timestamp[] interval:guard.intervals(current,adjustments))if(interval[1].after(now()))guard.assertStudentAvailable(student,interval[0],interval[1],-1,id(current.get("scheduleId")));
      }
      String old=text(bill.get("remark"));String note=(old.isBlank()?"":old+"\n")+"历史学员关联 studentId="+student+" actor="+actor+" reason="+why;
      require(note.length()<=500,"原备注与关联说明合计超过500字，请精简原因");
      save("edu_enrollment","enrollment_id",enrollment,values("student_id",student,"remark",note),actor);return row("edu_enrollment","enrollment_id",enrollment);
    });
  }
  public Map<String,Object> packages(long actor,boolean admin,Map<String,String> q) {
    actor(actor,admin);return page("edu_lesson_package"," WHERE del_flag='0'"+(admin?"":" AND status='0'"),new ArrayList<>(),q,Map.of("subjectCode","subject_code","packageId","package_id","status","status"));
  }
  public Map<String,Object> savePackage(long actor,Map<String,Object> body) {
    role(actor,"admin");return tx(()->{
      long product=body.get("packageId")==null?0:id(body.get("packageId"));if(product!=0)db.one("SELECT package_id FROM edu_lesson_package WHERE package_id=? AND del_flag='0' FOR UPDATE",product);
      String subject=str(body,"subjectCode"),name=dictionary("edu_subject",subject);
      product=save("edu_lesson_package","package_id",product,values("package_name",required(body.get("packageName"),100,"课包名称"),"subject_code",subject,"subject_name",name,"total_units",LessonRules.units(body.get("totalUnits")),"price",money(body.get("price")),"status",status(body)),actor);
      return row("edu_lesson_package","package_id",product);
    });
  }
  public Map<String,Object> studentPackages(long actor,boolean admin,Map<String,String> q) {
    actor(actor,admin);return page("edu_student_package"," WHERE 1=1"+(admin?"":" AND parent_id=?"),new ArrayList<>(admin?List.of():List.of(actor)),q,Map.of("studentId","student_id","parentId","parent_id","status","status","subjectCode","subject_code","studentPackageId","student_package_id"));
  }
  private Map<String,Object> previous(String table,long actor,String key,String hash) {
    var rows=db.rows("SELECT * FROM "+table+" WHERE parent_id=? AND request_key=?",actor,key);if(rows.isEmpty())return null;
    require(hash.equals(rows.get(0).get("requestHash")),"同一请求编号不能提交不同内容");return output(rows.get(0));
  }
  public Map<String,Object> purchase(long actor,Map<String,Object> body) {
    role(actor,"parent");long student=id(body.get("studentId")),product=id(body.get("packageId"));String key=key(body),fingerprint=hash(body);
    require(str(body,"userCouponId").isBlank(),"课包不使用课程优惠券");return tx(()->{
      var item=db.one("SELECT * FROM edu_lesson_package WHERE package_id=? FOR UPDATE",product);var child=guard.lockStudent(student,actor);
      var replay=previous("edu_student_package",actor,key,fingerprint);if(replay!=null)return replay;
      require("0".equals(item.get("status"))&&"0".equals(item.get("delFlag")),"课包已下架");dictionary("edu_subject",item.get("subjectCode"));
      long bill=save("edu_enrollment","enrollment_id",0,values("enrollment_code",code("LP").substring(0,32),"schedule_id",null,"parent_id",actor,"student_id",student,"student_name",child.get("studentName"),"student_phone",child.get("studentPhone"),"bill_type","LESSON_PACKAGE","lesson_package_id",product,"enrollment_status","报名成功","billing_status","OPEN","finance_mode",mockEnabled?"MOCK":"REAL","original_amount",money(item.get("price")),"discount_amount",money("0"),"payable_amount",money(item.get("price")),"fee_title_snapshot",child.get("studentName")+"＋"+item.get("subjectName")+"＋"+item.get("packageName"),"price_confirmed_by",actor,"price_confirmed_time",now()),actor);
      long packageId=save("edu_student_package","student_package_id",0,values("student_id",student,"parent_id",actor,"package_id",product,"enrollment_id",bill,"package_name_snapshot",item.get("packageName"),"subject_code",item.get("subjectCode"),"subject_name_snapshot",item.get("subjectName"),"total_units",item.get("totalUnits"),"available_units",0,"purchased_price",money(item.get("price")),"status","PENDING_PAYMENT","request_key",key,"request_hash",fingerprint),actor);
      return row("edu_student_package","student_package_id",packageId);
    });
  }
  public Map<String,Object> slots(long actor,boolean admin,boolean teacher,Map<String,String> q) {
    role(actor,admin?"admin":teacher?"teacher":"parent");String where=" WHERE 1=1";List<Object> args=new ArrayList<>();
    if(!admin&&teacher){where+=" AND teacher_id=?";args.add(actor);}else if(!admin){where+=" AND status='OPEN' AND start_time>? AND NOT EXISTS(SELECT 1 FROM edu_one_to_one_booking b WHERE b.slot_id=edu_one_to_one_slot.slot_id AND b.status IN('BOOKED','COMPLETED'))";args.add(now());}
    return page("edu_one_to_one_slot",where,args,q,Map.of("teacherId","teacher_id","subjectCode","subject_code","slotId","slot_id","status","status"));
  }
  private void validateResource(long teacher,long classroom) {role(teacher,"teacher");require("0".equals(db.one("SELECT status FROM edu_classroom WHERE classroom_id=?",classroom).get("status")),"教室已停用");}
  public Map<String,Object> saveSlot(long actor,boolean admin,Map<String,Object> body) {
    role(actor,admin?"admin":"teacher");long teacher=admin?id(body.get("teacherId")):actor,classroom=id(body.get("classroomId"));
    require(admin||body.get("teacherId")==null||id(body.get("teacherId"))==actor,"只能发布自己的时段");
    Timestamp start=time(body.get("startTime")),end=time(body.get("endTime"));LessonRules.duration(start,end);require(start.after(now()),"开放时段必须在未来");
    String subject=str(body,"subjectCode"),name=dictionary("edu_subject",subject),state=body.containsKey("status")?str(body,"status"):"OPEN";require(Set.of("OPEN","CLOSED").contains(state),"时段状态无效");
    long slot=body.get("slotId")==null?0:id(body.get("slotId"));return tx(()->{
      var old=slot==0?null:row("edu_one_to_one_slot","slot_id",slot);if(old!=null)require(admin||id(old.get("teacherId"))==actor,"无权操作其他教师时段");
      Set<Long> teachers=new TreeSet<>(List.of(teacher)),rooms=new TreeSet<>(List.of(classroom));if(old!=null){teachers.add(id(old.get("teacherId")));rooms.add(id(old.get("classroomId")));}
      guard.lockResources(teachers,rooms);validateResource(teacher,classroom);int version=0;
      if(old!=null){var current=db.one("SELECT * FROM edu_one_to_one_slot WHERE slot_id=? FOR UPDATE",slot);require(Objects.equals(old.get("teacherId"),current.get("teacherId"))&&Objects.equals(old.get("classroomId"),current.get("classroomId")),"时段资源已调整，请刷新");require(integer(body.get("version"),0,Integer.MAX_VALUE)==integer(current.get("version"),0,Integer.MAX_VALUE),"时段版本已更新，请刷新");require(LessonRules.beforeStart(now(),current.get("startTime")),"已开始时段不可修改");require(db.rows("SELECT booking_id FROM edu_one_to_one_booking WHERE slot_id=? AND status IN('BOOKED','COMPLETED')",slot).isEmpty(),"有有效预约，请先处理预约");version=integer(current.get("version"),0,Integer.MAX_VALUE)+1;}
      if("OPEN".equals(state))guard.assertResourceAvailable(teacher,classroom,start,end,slot,-1);
      long saved=save("edu_one_to_one_slot","slot_id",slot,values("teacher_id",teacher,"classroom_id",classroom,"subject_code",subject,"subject_name_snapshot",name,"start_time",start,"end_time",end,"status",state,"version",version),actor);return row("edu_one_to_one_slot","slot_id",saved);
    });
  }
  public Map<String,Object> bookings(long actor,boolean admin,boolean teacher,Map<String,String> q) {
    role(actor,admin?"admin":teacher?"teacher":"parent");return page("edu_one_to_one_booking"," WHERE 1=1"+(admin?"":teacher?" AND b.teacher_id=?":" AND b.parent_id=?"),new ArrayList<>(admin?List.of():List.of(actor)),q,Map.of("studentId","b.student_id","parentId","b.parent_id","teacherId","b.teacher_id","bookingId","b.booking_id","status","b.status"));
  }
  public Map<String,Object> book(long actor,Map<String,Object> body) {
    role(actor,"parent");long slot=id(body.get("slotId")),student=id(body.get("studentId")),packageId=id(body.get("studentPackageId"));String key=key(body),fingerprint=hash(body);
    return tx(()->{
      var replay=previous("edu_one_to_one_booking",actor,key,fingerprint);if(replay!=null)return replay;
      var before=row("edu_one_to_one_slot","slot_id",slot);long teacher=id(before.get("teacherId")),room=id(before.get("classroomId"));guard.lockResources(List.of(teacher),List.of(room));
      var available=db.one("SELECT * FROM edu_one_to_one_slot WHERE slot_id=? FOR UPDATE",slot);require(Objects.equals(before.get("teacherId"),available.get("teacherId"))&&Objects.equals(before.get("classroomId"),available.get("classroomId")),"时段已调整，请刷新重试");
      guard.lockStudent(student,actor);var p=db.one("SELECT * FROM edu_student_package WHERE student_package_id=? AND student_id=? AND parent_id=? FOR UPDATE",packageId,student,actor);
      replay=previous("edu_one_to_one_booking",actor,key,fingerprint);if(replay!=null)return replay;
      require("ACTIVE".equals(p.get("status")),"课包未付款、冻结或关闭，不能预约");require("OPEN".equals(available.get("status"))&&LessonRules.beforeStart(now(),available.get("startTime")),"时段关闭或已经开始");validateResource(teacher,room);LessonRules.subject(str(p,"subjectCode"),str(available,"subjectCode"));
      Timestamp start=time(available.get("startTime")),end=time(available.get("endTime"));LessonRules.duration(start,end);guard.assertResourceAvailable(teacher,room,start,end,slot,-1);guard.assertStudentAvailable(student,start,end,-1,-1);
      require(db.rows("SELECT booking_id FROM edu_one_to_one_booking WHERE slot_id=? AND status IN('BOOKED','COMPLETED')",slot).isEmpty(),"时段已被预约，请刷新");
      String level=(String)db.one("SELECT teacher_level FROM sys_user WHERE user_id=?",teacher).get("teacherLevel");int cost=LessonRules.cost(level);require(integer(p.get("availableUnits"),0,Integer.MAX_VALUE)>=cost,"课包剩余次数不足");
      long booking=save("edu_one_to_one_booking","booking_id",0,values("booking_code",code("LB"),"slot_id",slot,"student_id",student,"parent_id",actor,"student_package_id",packageId,"teacher_id",teacher,"teacher_level_snapshot",level,"subject_code",available.get("subjectCode"),"classroom_id",room,"start_time_snapshot",start,"end_time_snapshot",end,"deducted_units",cost,"returned_units",0,"request_key",key,"request_hash",fingerprint),actor);
      finance.change(p,booking,null,"BOOK",-cost,"BOOK:"+booking,actor,"预约扣次");return row("edu_one_to_one_booking","booking_id",booking);
    });
  }
  private List<Map<String,Object>> lockBooking(long booking,long actor,boolean admin) {
    var before=row("edu_one_to_one_booking","booking_id",booking);require(admin||id(before.get("parentId"))==actor,"无权操作此预约");
    // Cancellation/return do not wait for resources, slots or bills. Student -> package -> booking.
    // Existing disabled students can still receive lawful returns; ownership stays immutable.
    db.one("SELECT student_id FROM edu_student WHERE student_id=? AND parent_id=? FOR UPDATE",before.get("studentId"),before.get("parentId"));
    var p=db.one("SELECT * FROM edu_student_package WHERE student_package_id=? FOR UPDATE",before.get("studentPackageId"));var b=db.one("SELECT * FROM edu_one_to_one_booking WHERE booking_id=? FOR UPDATE",booking);return List.of(b,p);
  }
  private void returnRemaining(Map<String,Object> b,Map<String,Object> p,long actor,String event,String reason) {
    require("ACTIVE".equals(p.get("status")),"未收款、冻结或关闭课包不能退次");int count=integer(b.get("deductedUnits"),1,2)-integer(b.get("returnedUnits"),0,2);if(count==0)return;
    long booking=id(b.get("bookingId"));finance.change(p,booking,null,event,count,event+":"+booking,actor,reason);
    save("edu_one_to_one_booking","booking_id",booking,values("returned_units",b.get("deductedUnits")),actor);
  }
  public Map<String,Object> cancel(long actor,boolean admin,long bookingId,Map<String,Object> body) {
    actor(actor,admin);String why=reason(body,"reason",500);return tx(()->{
      var locked=lockBooking(bookingId,actor,admin);var b=locked.get(0);var p=locked.get(1);if("CANCELLED".equals(b.get("status")))return row("edu_one_to_one_booking","booking_id",bookingId);
      require("BOOKED".equals(b.get("status")),"已完成预约不能取消");boolean before=LessonRules.beforeStart(now(),b.get("startTimeSnapshot"));require(admin||before,"开课后家长不能自行取消");
      if(before)returnRemaining(b,p,actor,"CANCEL_RETURN",why);
      save("edu_one_to_one_booking","booking_id",bookingId,values("status","CANCELLED","cancelled_time",now(),"cancelled_by",actor,"cancel_reason",why),actor);return row("edu_one_to_one_booking","booking_id",bookingId);
    });
  }
  public Map<String,Object> returnUnits(long actor,long bookingId,Map<String,Object> body) {
    role(actor,"admin");String why=reason(body,"reason",500);return tx(()->{var locked=lockBooking(bookingId,actor,true);var b=locked.get(0);require("CANCELLED".equals(b.get("status")),"请先登记取消，再独立退次");returnRemaining(b,locked.get(1),actor,"ADMIN_RETURN",why);return row("edu_one_to_one_booking","booking_id",bookingId);});
  }
  public Map<String,Object> complete(long actor,long bookingId,Map<String,Object> body) {
    role(actor,"admin");return tx(()->{var b=lockBooking(bookingId,actor,true).get(0);require(!LessonRules.beforeStart(now(),b.get("endTimeSnapshot")),"课程未结束，不能登记完成");require(Set.of("BOOKED","COMPLETED").contains(b.get("status")),"已取消预约不能登记完成");save("edu_one_to_one_booking","booking_id",bookingId,values("status","COMPLETED"),actor);return row("edu_one_to_one_booking","booking_id",bookingId);});
  }
  public Map<String,Object> setTeacherLevel(long actor,Map<String,Object> body) {
    role(actor,"admin");long teacher=id(body.get("teacherId"));String level=Objects.toString(body.get("level"),"");LessonRules.cost(level);return tx(()->{
      guard.lockResources(List.of(teacher),List.of());role(teacher,"teacher");
      save("sys_user","user_id",teacher,values("teacher_level",level),actor);
      return values("teacherId",String.valueOf(teacher),"teacherLevel",level);
    });
  }
}

package com.ruoyi.system.lesson;

import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.system.finance.FinanceRules.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.shop.ShopRepository;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** All balances, locks, rollback and race assertions use the designated real MySQL schema. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LessonMysqlTest {
  LessonSchemaMysqlTest fixture = new LessonSchemaMysqlTest();
  JdbcTemplate db;
  ShopRepository repo;
  Object service, finance;
  @BeforeAll void enabled() { Assumptions.assumeTrue(Boolean.getBoolean("lesson.mysql")); }
  @BeforeEach void setup() throws Exception {
    fixture.resetFixture(); db=fixture.db;
    db.execute("ALTER TABLE sys_user ADD teacher_level varchar(20) NULL, ADD teacher_subject varchar(20) NULL");
    db.execute("UPDATE sys_user SET teacher_level='elite' WHERE user_id IN(4,5)");
    fixture.migrateSuccessfully();
    String schema=Files.readString(Path.of("../../database/schema.sql"));
    for(String table:List.of("sys_user_role","sys_dict_data","edu_schedule_adjustment")) {
      Matcher m=Pattern.compile("CREATE TABLE `"+table+"` \\(.*?\\) ENGINE=.*?;",Pattern.DOTALL).matcher(schema);
      assertTrue(m.find()); db.execute(m.group());
    }
    db.execute("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status) VALUES(3,'教师','teacher',3,'0')");
    db.execute("INSERT INTO sys_user_role VALUES(1,1),(2,2),(3,2),(4,3),(5,3)");
    db.execute("INSERT INTO sys_dict_data(dict_type,dict_value,dict_label) VALUES('edu_subject','math','数学'),('edu_subject','english','英语'),('edu_grade','grade5','五年级')");
    db.execute("UPDATE edu_course_schedule SET status='1' WHERE schedule_id=1");
    db.execute("INSERT INTO edu_classroom(classroom_id,classroom_code,campus_name,classroom_name) VALUES(2,'TEST2','测试校区','B')");
    repo=new ShopRepository(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/"+LessonSchemaMysqlTest.SCHEMA+"?serverTimezone=Asia/Shanghai", "root", ""));
    try {
      Class<?> guard=Class.forName("com.ruoyi.system.lesson.LessonConflictGuard");
      Object g=guard.getConstructor(ShopRepository.class).newInstance(repo);
      service=Class.forName("com.ruoyi.system.lesson.LessonService").getConstructor(ShopRepository.class,guard,boolean.class).newInstance(repo,g,true);
      finance=Class.forName("com.ruoyi.system.lesson.LessonFinance").getConstructor(ShopRepository.class).newInstance(repo);
    } catch(ClassNotFoundException e) { fail("Lesson transaction core is missing"); }
  }
  @AfterAll void cleanup() throws Exception { fixture.cleanup(); }
  @SuppressWarnings("unchecked") Map<String,Object> call(String name,Object... args) {
    try {
      Method method=Arrays.stream(service.getClass().getMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();
      return (Map<String,Object>)method.invoke(service,args);
    } catch(InvocationTargetException e) { if(e.getCause() instanceof RuntimeException r) throw r; throw new RuntimeException(e.getCause()); }
    catch(Exception e) { throw new RuntimeException(e); }
  }
  void hook(String name,long enrollment,Object... args) {
    repo.transactions().execute(s->{
      try {
        Object[] all=new Object[args.length+1]; all[0]=enrollment; System.arraycopy(args,0,all,1,args.length);
        Arrays.stream(finance.getClass().getMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow().invoke(finance,all);
        return null;
      } catch(InvocationTargetException e) { if(e.getCause() instanceof RuntimeException r) throw r; throw new RuntimeException(e.getCause()); }
      catch(Exception e) { throw new RuntimeException(e); }
    });
  }
  long student(long parent) {return id(call("saveStudent",parent,false,values("studentName","同名","gradeCode","grade5")).get("studentId"));}
  long product(String subject,int units) {return id(call("savePackage",1L,values("packageName","课包","subjectCode",subject,"totalUnits",units,"price","100.00")).get("packageId"));}
  Map<String,Object> buy(long parent,long student,long product,String key) {return call("purchase",parent,values("studentId",student,"packageId",product,"idempotencyKey",key,"financeMode","REAL","price","0"));}
  long active(long parent,long student,long product,String key) {
    var p=buy(parent,student,product,key); paid(id(p.get("enrollmentId"))); hook("activate",id(p.get("enrollmentId")),1L); return id(p.get("studentPackageId"));
  }
  long paid(long enrollment) {
    String mode=text(repo.one("SELECT finance_mode FROM edu_enrollment WHERE enrollment_id=?",enrollment).get("financeMode"));
    return repo.insert("edu_tuition_payment",values("payment_no",code("TP"),"enrollment_id",enrollment,"channel",mode.equals("MOCK")?"MOCK":"BANK_TRANSFER","payment_status","SUCCESS","finance_mode",mode,"amount","100.00","payer_name","家长","bill_snapshot","{}","recorded_by",1L,"confirmed_by",1L,"confirmed_time",now(),"paid_time",now(),"idempotency_key",code("P"),"request_hash",hash("payment")));
  }
  long slot(long teacher,long room,String start) {return id(call("saveSlot",1L,true,values("teacherId",teacher,"classroomId",room,"subjectCode","math","startTime",start,"endTime",java.sql.Timestamp.valueOf(start).toLocalDateTime().plusHours(2).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))).get("slotId"));}
  Map<String,Object> book(long parent,long student,long pack,long slot,String key) {return call("book",parent,values("studentId",student,"studentPackageId",pack,"slotId",slot,"idempotencyKey",key));}
  int balance(long p) {return db.queryForObject("SELECT available_units FROM edu_student_package WHERE student_package_id=?",Integer.class,p);}
  void ledger(long p,int expect) {
    assertEquals(expect,balance(p));
    assertEquals(expect,db.queryForObject("SELECT COALESCE(SUM(delta_units),0) FROM edu_lesson_unit_log WHERE student_package_id=?",Integer.class,p));
  }
  @Test void purchaseFreezesTrustedSnapshotAndIdempotency() {
    long s=student(2), p=product("math",10); var a=buy(2,s,p,"BUY");
    assertEquals("PENDING_PAYMENT",a.get("status")); assertEquals("100.00",a.get("purchasedPrice"));
    var e=repo.one("SELECT * FROM edu_enrollment WHERE enrollment_id=?",a.get("enrollmentId"));
    assertEquals("MOCK",e.get("financeMode")); assertEquals("100.00",e.get("payableAmount")); assertNull(e.get("scheduleId"));
    assertEquals(a.get("studentPackageId"),buy(2,s,p,"BUY").get("studentPackageId"));
    assertThrows(ServiceException.class,()->buy(2,student(2),p,"BUY"));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM edu_student_package",Integer.class));
  }
  @Test void paidActivationExactlyOnceAndNotBeforeConfirmation() {
    var p=buy(2,student(2),product("math",10),"BUY"); long e=id(p.get("enrollmentId")), packageId=id(p.get("studentPackageId"));
    assertThrows(ServiceException.class,()->hook("activate",e,1L)); ledger(packageId,0);
    paid(e); hook("activate",e,1L); hook("activate",e,1L); ledger(packageId,10);
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM edu_lesson_unit_log",Integer.class));
  }
  @Test void bookingCostsSnapshotsCancellationReturnsOnceAndChangedRequestRejected() {
    long s=student(2),p=active(2,s,product("math",10),"BUY"),slot=slot(4,1,"2099-10-11 10:00:00");
    var b=book(2,s,p,slot,"BOOK"); assertEquals(1,b.get("deductedUnits")); ledger(p,9);
    var teacher=assertDoesNotThrow(()->call("setTeacherLevel",1L,values("teacherId",4L,"level","senior")));
    assertEquals("4",teacher.get("teacherId"));assertEquals("senior",teacher.get("teacherLevel"));
    assertEquals(b.get("bookingId"),book(2,s,p,slot,"BOOK").get("bookingId"));
    assertThrows(ServiceException.class,()->book(2,s,p,slot+1,"BOOK"));
    call("cancel",2L,false,id(b.get("bookingId")),values("reason","改约"));
    call("cancel",2L,false,id(b.get("bookingId")),values("reason","改约")); ledger(p,10);
    var senior=book(2,s,p,slot,"BOOK2");assertEquals(2,senior.get("deductedUnits"));assertEquals("senior",senior.get("teacherLevelSnapshot"));ledger(p,8);
    call("setTeacherLevel",1L,values("teacherId",4L,"level","elite"));
    assertEquals("senior",book(2,s,p,slot,"BOOK2").get("teacherLevelSnapshot"));
    call("cancel",2L,false,id(senior.get("bookingId")),values("reason","课前取消"));
    call("cancel",2L,false,id(senior.get("bookingId")),values("reason","课前取消"));ledger(p,10);
    assertEquals(2,db.queryForObject("SELECT returned_units FROM edu_one_to_one_booking WHERE booking_id=?",Integer.class,senior.get("bookingId")));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM edu_lesson_unit_log WHERE booking_id=? AND delta_units=2",Integer.class,senior.get("bookingId")));
  }
  @Test void rejectsOwnershipSubjectPendingAndFrozen() {
    long s=student(2), sibling=student(2),other=student(3), p=product("math",10), slot=slot(4,1,"2099-10-11 10:00:00");
    var pending=buy(2,s,p,"BUY"); long pack=id(pending.get("studentPackageId"));
    assertThrows(ServiceException.class,()->book(2,s,pack,slot,"B1"));
    paid(id(pending.get("enrollmentId")));hook("activate",id(pending.get("enrollmentId")),1L);
    assertThrows(ServiceException.class,()->book(2,sibling,pack,slot,"B2"));
    assertThrows(ServiceException.class,()->book(3,other,pack,slot,"B3"));
    long english=active(2,s,product("english",10),"BUY2");
    assertThrows(ServiceException.class,()->book(2,s,english,slot,"B4"));
    db.update("UPDATE edu_student_package SET status='REFUND_FROZEN' WHERE student_package_id=?",pack);
    assertThrows(ServiceException.class,()->book(2,s,pack,slot,"B5")); ledger(pack,10);
  }
  @Test void transactionalFailureRollsBackBookingAndBalance() {
    long s=student(2),p=active(2,s,product("math",10),"BUY"),sl=slot(4,1,"2099-10-11 10:00:00");
    db.execute("CREATE TRIGGER test_log_failure BEFORE INSERT ON edu_lesson_unit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test rollback'");
    assertThrows(RuntimeException.class,()->book(2,s,p,sl,"B")); ledger(p,10);
    assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM edu_one_to_one_booking",Integer.class));
  }
  List<Boolean> race(Callable<?> first,Callable<?> second) throws Exception {
    ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch ready=new CountDownLatch(2), go=new CountDownLatch(1);
    try {
      List<Future<Boolean>> results=new ArrayList<>();
      for(Callable<?> task:List.of(first,second)) results.add(pool.submit(()->{ready.countDown();go.await();try {task.call();return true;}catch(ServiceException e){return false;}}));
      assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
      return List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS));
    } finally {pool.shutdownNow();}
  }
  @RepeatedTest(3) void twoParentsRaceForSlotOnlyOneWins() throws Exception {
    long s1=student(2),s2=student(3),product=product("math",10),p1=active(2,s1,product,"P1"),p2=active(3,s2,product,"P2"),sl=slot(4,1,"2099-10-11 10:00:00");
    assertEquals(1,Collections.frequency(race(()->book(2,s1,p1,sl,"B1"),()->book(3,s2,p2,sl,"B2")),true));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM edu_one_to_one_booking",Integer.class));
    ledger(p1,balance(p1));ledger(p2,balance(p2));assertEquals(19,balance(p1)+balance(p2));
  }
  @RepeatedTest(3) void samePackageConcurrentBookingsCannotOverdraw() throws Exception {
    long s=student(2),p=active(2,s,product("math",1),"P"),a=slot(4,1,"2099-10-11 10:00:00"),b=slot(5,2,"2099-10-12 10:00:00");
    assertEquals(1,Collections.frequency(race(()->book(2,s,p,a,"B1"),()->book(2,s,p,b,"B2")),true));ledger(p,0);
  }
  @RepeatedTest(3) void studentLockPreventsOverlapsAcrossTwoPackages() throws Exception {
    long s=student(2),product=product("math",10),p1=active(2,s,product,"P1"),p2=active(2,s,product,"P2"),a=slot(4,1,"2099-10-11 10:00:00"),b=slot(5,2,"2099-10-11 10:00:00");
    assertEquals(1,Collections.frequency(race(()->book(2,s,p1,a,"B1"),()->book(2,s,p2,b,"B2")),true));assertEquals(19,balance(p1)+balance(p2));
  }
  @Test void adminAfterStartCancellationHasSeparateAuditedReturnAndCompletedIsOccupied() {
    long s=student(2),p=active(2,s,product("math",10),"P"),a=slot(4,1,"2099-10-11 10:00:00");
    long b=id(book(2,s,p,a,"B").get("bookingId"));
    db.update("UPDATE edu_one_to_one_booking SET start_time_snapshot='2000-01-01 10:00:00',end_time_snapshot='2000-01-01 12:00:00' WHERE booking_id=?",b);
    assertThrows(ServiceException.class,()->call("cancel",2L,false,b,values("reason","取消")));
    call("cancel",1L,true,b,values("reason","事后取消"));ledger(p,9);
    assertThrows(ServiceException.class,()->call("returnUnits",1L,b,values("reason","")));
    call("returnUnits",1L,b,values("reason","凭证核实"));call("returnUnits",1L,b,values("reason","凭证核实"));ledger(p,10);
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM edu_lesson_unit_log WHERE event_type='ADMIN_RETURN'",Integer.class));
    long c=id(book(2,s,p,a,"B2").get("bookingId"));
    assertThrows(ServiceException.class,()->call("complete",1L,c,values()));
    db.update("UPDATE edu_one_to_one_booking SET start_time_snapshot='2000-01-01 10:00:00',end_time_snapshot='2000-01-01 12:00:00' WHERE booking_id=?",c);
    call("complete",1L,c,values());ledger(p,9);assertThrows(ServiceException.class,()->book(2,s,p,a,"B3"));
  }
  long refund(long enrollment,String state) {
    long payment=db.queryForObject("SELECT payment_id FROM edu_tuition_payment WHERE enrollment_id=?",Long.class,enrollment);
    return repo.insert("edu_tuition_refund",values("refund_no",code("TR"),"payment_id",payment,"refund_kind","WITHDRAWAL","requested_amount","10.00","approved_amount","10.00","refund_status",state,"reason","协商终止","calculation_snapshot","{}","applicant_id",1L,"idempotency_key",code("R"),"request_hash",hash("refund")));
  }
  @Test void refundRejectsUnfinishedBookingsThenFreezesUnknownAndClosesExactlyOnce() {
    long s=student(2),p=active(2,s,product("math",10),"P"),e=db.queryForObject("SELECT enrollment_id FROM edu_student_package WHERE student_package_id=?",Long.class,p),sl=slot(4,1,"2099-10-11 10:00:00");
    long b=id(book(2,s,p,sl,"B").get("bookingId")),r=refund(e,"PENDING_REVIEW");
    assertThrows(ServiceException.class,()->hook("freeze",e,r,1L));
    call("cancel",2L,false,b,values("reason","取消后退款"));hook("freeze",e,r,1L);ledger(p,10);
    assertThrows(ServiceException.class,()->call("returnUnits",1L,b,values("reason","补退")));
    db.update("UPDATE edu_tuition_refund SET refund_status='UNKNOWN' WHERE refund_id=?",r);
    assertThrows(ServiceException.class,()->hook("unfreeze",e,1L));assertThrows(ServiceException.class,()->hook("close",e,r,1L));ledger(p,10);
    db.update("UPDATE edu_tuition_refund SET refund_status='SUCCESS',completed_time=NOW(3) WHERE refund_id=?",r);
    hook("close",e,r,1L);hook("close",e,r,1L);ledger(p,0);
    assertEquals("CLOSED",repo.one("SELECT status FROM edu_student_package WHERE student_package_id=?",p).get("status"));
    assertThrows(ServiceException.class,()->hook("activate",e,1L));assertThrows(ServiceException.class,()->call("returnUnits",1L,b,values("reason","补退")));
  }
  @Test void rejectedRefundUnfreezesOnlyFrozenPackageAndCourseHooksAreNoOp() {
    long s=student(2),p=active(2,s,product("math",10),"P"),e=db.queryForObject("SELECT enrollment_id FROM edu_student_package WHERE student_package_id=?",Long.class,p),r=refund(e,"PENDING_REVIEW");
    hook("freeze",e,r,1L);db.update("UPDATE edu_tuition_refund SET refund_status='REJECTED' WHERE refund_id=?",r);hook("unfreeze",e,1L);hook("unfreeze",e,1L);ledger(p,10);
    assertEquals("ACTIVE",repo.one("SELECT status FROM edu_student_package WHERE student_package_id=?",p).get("status"));
    hook("activate",1L,1L);hook("freeze",1L,999L,1L);hook("unfreeze",1L,1L);hook("close",1L,999L,1L);
  }
  @Test void unknownHistoricalOccupancyRejectsPublishingAndDatedCourseConflicts() {
    db.execute("UPDATE edu_course_schedule SET status='0',teacher_id=4 WHERE schedule_id=1");
    assertThrows(ServiceException.class,()->slot(4,1,"2099-10-11 10:00:00"));
    db.execute("UPDATE edu_course_schedule SET start_date='2099-10-11',end_date='2099-10-11',period_name='周日',class_pattern='DAILY_5_1',start_time='10:00:00',end_time='12:00:00' WHERE schedule_id=1");
    assertThrows(ServiceException.class,()->slot(4,1,"2099-10-11 11:00:00"));
    assertDoesNotThrow(()->slot(4,1,"2099-10-11 12:00:00"));
  }
  @Test void legacyCourseStudentConflictAndSlotVersionAndAuthorization() {
    long s=student(2),p=active(2,s,product("math",10),"P"),a=slot(4,1,"2099-10-11 10:00:00");
    db.execute("UPDATE edu_course_schedule SET status='0',teacher_id=5,classroom_id=2,start_date='2099-10-11',end_date='2099-10-11',class_pattern='DAILY_5_1',start_time='10:00:00',end_time='12:00:00' WHERE schedule_id=1");
    db.update("UPDATE edu_enrollment SET student_id=? WHERE enrollment_id=1",s);
    assertThrows(ServiceException.class,()->book(2,s,p,a,"B"));
    assertThrows(ServiceException.class,()->call("saveSlot",1L,true,values("slotId",a,"version",99,"teacherId",4L,"classroomId",1L,"subjectCode","math","startTime","2099-10-12 10:00:00","endTime","2099-10-12 12:00:00")));
    assertThrows(ServiceException.class,()->call("savePackage",4L,values("packageName","非法","subjectCode","math","totalUnits",10,"price","100")));
    assertThrows(ServiceException.class,()->call("setTeacherLevel",4L,values("teacherId",4,"level","senior")));
    assertThrows(ServiceException.class,()->call("saveSlot",4L,false,values("teacherId",5L,"classroomId",1L,"subjectCode","math","startTime","2099-10-12 10:00:00","endTime","2099-10-12 12:00:00")));
    assertEquals(1L,call("students",2L,false,Map.of()).get("total"));assertEquals(0L,call("students",3L,false,Map.of()).get("total"));
  }
  @Test void requestKeyRejectsChangedBodyEvenForIgnoredClientOverrides() {
    long s=student(2),p=product("math",10);buy(2,s,p,"P");
    assertThrows(ServiceException.class,()->call("purchase",2L,values("studentId",s,"packageId",p,"idempotencyKey","P","financeMode","MOCK","price","0")));
    long pack=active(2,s,p,"P2"),sl=slot(4,1,"2099-10-11 10:00:00");book(2,s,pack,sl,"B");
    assertThrows(ServiceException.class,()->call("book",2L,values("studentId",s,"studentPackageId",pack,"slotId",sl,"idempotencyKey","B","deductedUnits",0)));
  }
  @Test void historicalLinkPreservesNamesAndMoneyAndRejectsFutureBookingConflict() {
    long s=student(2),pack=active(2,s,product("math",10),"P"),sl=slot(4,1,"2099-10-11 10:00:00");book(2,s,pack,sl,"B");
    db.execute("UPDATE edu_course_schedule SET status='0',teacher_id=5,classroom_id=2,start_date='2099-10-11',end_date='2099-10-11',class_pattern='DAILY_5_1',start_time='10:00:00',end_time='12:00:00' WHERE schedule_id=1");
    assertThrows(ServiceException.class,()->call("linkEnrollment",1L,values("enrollmentId",1L,"studentId",s,"reason","人工核对")));
    assertNull(repo.one("SELECT student_id FROM edu_enrollment WHERE enrollment_id=1").get("studentId"));
    db.execute("UPDATE edu_course_schedule SET start_date='2000-01-01',end_date='2000-01-01' WHERE schedule_id=1");
    var linked=call("linkEnrollment",1L,values("enrollmentId",1L,"studentId",s,"reason","人工核对"));
    assertEquals(String.valueOf(s),linked.get("studentId"));assertEquals("历史学生",linked.get("studentName"));assertNull(linked.get("originalAmount"));
    assertTrue(text(linked.get("remark")).contains("人工核对"));
    assertThrows(ServiceException.class,()->call("linkEnrollment",1L,values("enrollmentId",1L,"studentId",student(2),"reason","试图替换")));
  }
  @Test void serverChoosesRealModeAndCanonicalGradeWorksWithoutSalaryConfig() throws Exception {
    org.springframework.core.env.StandardEnvironment env=new org.springframework.core.env.StandardEnvironment();env.setActiveProfiles("local","prod");
    env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("lessonTest",Map.of("payment.mode","mock")));
    Class<?> guard=Class.forName("com.ruoyi.system.lesson.LessonConflictGuard");Object g=guard.getConstructor(ShopRepository.class).newInstance(repo);
    service=service.getClass().getConstructor(ShopRepository.class,guard,org.springframework.core.env.Environment.class).newInstance(repo,g,env);
    long s=student(2);var p=call("purchase",2L,values("studentId",s,"packageId",product("math",10),"idempotencyKey","P","financeMode","MOCK"));
    assertEquals("REAL",repo.one("SELECT finance_mode FROM edu_enrollment WHERE enrollment_id=?",p.get("enrollmentId")).get("financeMode"));
    paid(id(p.get("enrollmentId")));hook("activate",id(p.get("enrollmentId")),1L);
    db.execute("DELETE FROM edu_teacher_salary_config WHERE teacher_id=4");
    db.execute("UPDATE sys_user SET teacher_level='senior' WHERE user_id=4");
    var b=book(2,s,id(p.get("studentPackageId")),slot(4,1,"2099-10-11 10:00:00"),"B");assertEquals(2,b.get("deductedUnits"));assertEquals("senior",b.get("teacherLevelSnapshot"));
    call("setTeacherLevel",1L,values("teacherId",4L,"level","elite"));
    assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM edu_teacher_salary_config WHERE teacher_id=4",Integer.class));
  }
  @Test void canonicalGradeOverridesConflictingSalaryDataAndNeverDefaultsUnknownGrade() {
    if(db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='edu_teacher_salary_config' AND column_name='one_to_one_level'",Integer.class)==0)
      db.execute("ALTER TABLE edu_teacher_salary_config ADD one_to_one_level varchar(6) NOT NULL DEFAULT 'NORMAL'");
    long s=student(2),p=active(2,s,product("math",10),"P"),sl=slot(4,1,"2099-10-11 10:00:00");
    for(String unknown:new String[]{null,"","NORMAL","senior ","SENIOR"}) {
      db.update("UPDATE sys_user SET teacher_level=? WHERE user_id=4",unknown);
      var error=assertThrows(ServiceException.class,()->book(2,s,p,sl,"B"));
      assertTrue(error.getMessage().contains("管理员"));ledger(p,10);
    }
    assertThrows(ServiceException.class,()->call("setTeacherLevel",1L,values("teacherId",4L,"level","NORMAL")));
    db.execute("UPDATE sys_user SET teacher_level='senior' WHERE user_id=4");
    var b=book(2,s,p,sl,"B");assertEquals(2,b.get("deductedUnits"));assertEquals("senior",b.get("teacherLevelSnapshot"));ledger(p,8);
    call("setTeacherLevel",1L,values("teacherId",4L,"level","elite"));
    assertEquals("NORMAL",db.queryForObject("SELECT one_to_one_level FROM edu_teacher_salary_config WHERE teacher_id=4",String.class));
  }
  @Test void financeFreezeCurrentReadSeesBookingAfterOldRepeatableReadSnapshot() throws Exception {
    long s=student(2),p=active(2,s,product("math",10),"P"),e=db.queryForObject("SELECT enrollment_id FROM edu_student_package WHERE student_package_id=?",Long.class,p),sl=slot(4,1,"2099-10-11 10:00:00"),r=refund(e,"PENDING_REVIEW");
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try {
      assertThrows(ServiceException.class,()->repo.transactions().execute(status->{
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM edu_one_to_one_booking",Integer.class));
        try {pool.submit(()->book(2,s,p,sl,"B")).get(15,TimeUnit.SECONDS);}catch(Exception ex){throw new RuntimeException(ex);}
        hook("freeze",e,r,1L);return null;
      }));
    }finally{pool.shutdownNow();}
    ledger(p,9);assertEquals("ACTIVE",repo.one("SELECT status FROM edu_student_package WHERE student_package_id=?",p).get("status"));
  }
  @Test void cancellationAndRefundCloseRollbackAllStateIfLedgerInsertFails() {
    long s=student(2),p=active(2,s,product("math",10),"P"),e=db.queryForObject("SELECT enrollment_id FROM edu_student_package WHERE student_package_id=?",Long.class,p),sl=slot(4,1,"2099-10-11 10:00:00"),b=id(book(2,s,p,sl,"B").get("bookingId"));
    db.execute("CREATE TRIGGER test_log_failure BEFORE INSERT ON edu_lesson_unit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test rollback'");
    assertThrows(RuntimeException.class,()->call("cancel",2L,false,b,values("reason","取消")));ledger(p,9);
    assertEquals("BOOKED",repo.one("SELECT status FROM edu_one_to_one_booking WHERE booking_id=?",b).get("status"));
    db.execute("DROP TRIGGER test_log_failure");call("cancel",2L,false,b,values("reason","取消"));long r=refund(e,"PENDING_REVIEW");hook("freeze",e,r,1L);
    db.update("UPDATE edu_tuition_refund SET refund_status='SUCCESS',completed_time=NOW(3) WHERE refund_id=?",r);
    db.execute("CREATE TRIGGER test_log_failure BEFORE INSERT ON edu_lesson_unit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test rollback'");
    assertThrows(RuntimeException.class,()->hook("close",e,r,1L));ledger(p,10);assertEquals("REFUND_FROZEN",repo.one("SELECT status FROM edu_student_package WHERE student_package_id=?",p).get("status"));
  }
  @Test @SuppressWarnings("unchecked") void everyActorIdIsStringAndTeacherSeesOnlyOwnNecessaryStudentName() {
    long s=student(2),p=active(2,s,product("math",10),"P"),sl=slot(4,1,"2099-10-11 10:00:00"),b=id(book(2,s,p,sl,"B").get("bookingId"));call("cancel",2L,false,b,values("reason","取消"));
    var rows=(List<Map<String,Object>>)call("bookings",4L,false,true,Map.of()).get("rows");assertEquals(1,rows.size());
    assertEquals("2",rows.get(0).get("cancelledBy"));assertEquals("同名",rows.get(0).get("studentName"));
    assertEquals(0L,call("bookings",5L,false,true,Map.of()).get("total"));
    var bill=call("linkEnrollment",1L,values("enrollmentId",9L,"studentId",student(3),"reason","核实原报名"));assertEquals("1",bill.get("priceConfirmedBy"));
  }
  @Test void productEditsLeaveSoldSnapshotAndDisableBlockedWhileUnitsRemain() {
    long s=student(2),product=product("math",10),p=active(2,s,product,"P");
    call("savePackage",1L,values("packageId",product,"packageName","新版课包","subjectCode","english","totalUnits",20,"price","200.00","status","1"));
    var old=repo.one("SELECT * FROM edu_student_package WHERE student_package_id=?",p);assertEquals("math",old.get("subjectCode"));assertEquals(10,old.get("totalUnits"));assertEquals("100.00",old.get("purchasedPrice"));
    assertThrows(ServiceException.class,()->buy(2,s,product,"P2"));
    assertThrows(ServiceException.class,()->call("saveStudent",2L,false,values("studentId",s,"studentName","停用","status","1")));
    assertThrows(ServiceException.class,()->call("savePackage",1L,values("packageName","非法","subjectCode","数学","totalUnits",10,"price","100")));
    assertThrows(ServiceException.class,()->call("students",2L,false,Map.of("pageSize","101")));
    ledger(p,10);
  }
}

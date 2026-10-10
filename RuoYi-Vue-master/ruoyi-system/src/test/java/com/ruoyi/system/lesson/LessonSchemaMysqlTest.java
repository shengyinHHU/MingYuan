package com.ruoyi.system.lesson;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Real MySQL schema contract; never connects to a business database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LessonSchemaMysqlTest {
  static final String SCHEMA = "codex_one_to_one_test_20261010";
  static final Path MIGRATION = Path.of("../../database/migrations/20261010_one_to_one.sql");
  JdbcTemplate db;

  @BeforeAll
  void setup() throws Exception {
    Assumptions.assumeTrue(Boolean.getBoolean("lesson.mysql"), "Enable with -Dlesson.mysql=true");
    resetFixture();
  }

  void resetFixture() throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "")) {
      c.createStatement().execute("DROP DATABASE IF EXISTS " + SCHEMA);
      c.createStatement().execute("CREATE DATABASE " + SCHEMA + " CHARACTER SET utf8mb4");
    }
    db = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:mysql://127.0.0.1:3306/" + SCHEMA + "?serverTimezone=Asia/Shanghai", "root", ""));
    String schema = Files.readString(Path.of("../../database/schema.sql"));
    for (String table : List.of("sys_user", "sys_role", "sys_menu", "sys_role_menu", "sys_config",
        "edu_classroom", "edu_course_schedule", "edu_enrollment", "edu_teacher_salary_config")) {
      Matcher m = Pattern.compile("CREATE TABLE `" + table + "` \\(.*?\\) ENGINE=.*?;", Pattern.DOTALL).matcher(schema);
      assertTrue(m.find(), table);
      db.execute(m.group());
    }
    db.execute("INSERT INTO sys_user(user_id,user_name,nick_name) VALUES(1,'admin','管理员'),(2,'parent','家长'),(3,'other','另一家长'),(4,'teacher','普通教师'),(5,'lead','负责人')");
    db.execute("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status) VALUES(1,'管理员','admin',1,'0'),(2,'家长','parent',2,'0')");
    db.execute("INSERT INTO edu_classroom(classroom_id,classroom_code,campus_name,classroom_name) VALUES(1,'TEST','测试校区','A')");
    db.execute("INSERT INTO edu_course_schedule(schedule_id,schedule_code,classroom_id,term_name,period_name,time_slot,grade_name,subject_name,course_class_name,enrolled_count) VALUES(1,'TEST',1,'秋季','周日','上午','五年级','数学','原课程',7)");
    db.execute("INSERT INTO edu_enrollment(enrollment_code,schedule_id,parent_id,student_name,pay_status) VALUES('HISTORY',1,2,'历史学生','已支付')");
    assertEquals(0, migrate(Path.of("../../database/migrations/20261007_tuition_finance.sql")).exit);
    db.execute("UPDATE edu_course_schedule SET tuition_price=1234.56 WHERE schedule_id=1");
    db.execute("INSERT INTO edu_enrollment(enrollment_id,enrollment_code,schedule_id,parent_id,student_name,original_amount,discount_amount,payable_amount,billing_status,price_confirmed_by,price_confirmed_time) VALUES(9,'PRICED',1,3,'已定价学生',500.00,50.00,450.00,'OPEN',1,'2026-10-10 08:00:00')");
    db.execute("INSERT INTO edu_teacher_salary_config(teacher_id,teacher_name,base_salary) VALUES(4,'普通教师',3000.00),(5,'负责人',4000.00)");
  }

  record Result(int exit, String output) {}

  Result migrate(Path path) throws Exception {
    assertTrue(Files.isRegularFile(path), "Required migration missing: " + path);
    Process p = new ProcessBuilder("/opt/homebrew/bin/mysql", "--protocol=TCP", "-h127.0.0.1", "-uroot", SCHEMA)
        .redirectErrorStream(true).start();
    try (var out = p.getOutputStream()) { out.write(Files.readAllBytes(path)); }
    String output = new String(p.getInputStream().readAllBytes());
    return new Result(p.waitFor(), output);
  }

  void migrateSuccessfully() throws Exception {
    Result result = migrate(MIGRATION);
    assertEquals(0, result.exit, result.output);
  }

  void checkRejected(String sql) {
    var error = assertThrows(org.springframework.dao.DataAccessException.class, () -> db.execute(sql));
    assertInstanceOf(SQLException.class, error.getMostSpecificCause());
    assertEquals(3819, ((SQLException) error.getMostSpecificCause()).getErrorCode(), error.getMessage());
  }

  @AfterAll
  void cleanup() throws Exception {
    if (db != null) try (Connection c = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "")) {
      c.createStatement().execute("DROP DATABASE IF EXISTS " + SCHEMA);
    }
  }

  @Test @Order(1)
  void firstAndRepeatedMigrationPreserveHistoryAndInstallPermissions() throws Exception {
    migrateSuccessfully();
    migrateSuccessfully();
    assertEquals("COURSE", db.queryForObject("SELECT bill_type FROM edu_enrollment WHERE enrollment_code='HISTORY'", String.class));
    assertNull(db.queryForObject("SELECT student_id FROM edu_enrollment WHERE enrollment_code='HISTORY'", Long.class));
    assertNull(db.queryForObject("SELECT original_amount FROM edu_enrollment WHERE enrollment_code='HISTORY'", java.math.BigDecimal.class));
    assertEquals("已支付", db.queryForObject("SELECT pay_status FROM edu_enrollment WHERE enrollment_code='HISTORY'", String.class));
    assertEquals("500.00", db.queryForObject("SELECT original_amount FROM edu_enrollment WHERE enrollment_code='PRICED'", java.math.BigDecimal.class).toPlainString());
    assertEquals("50.00", db.queryForObject("SELECT discount_amount FROM edu_enrollment WHERE enrollment_code='PRICED'", java.math.BigDecimal.class).toPlainString());
    assertEquals("450.00", db.queryForObject("SELECT payable_amount FROM edu_enrollment WHERE enrollment_code='PRICED'", java.math.BigDecimal.class).toPlainString());
    assertEquals("1234.56", db.queryForObject("SELECT tuition_price FROM edu_course_schedule WHERE schedule_id=1", java.math.BigDecimal.class).toPlainString());
    assertEquals(7, db.queryForObject("SELECT enrolled_count FROM edu_course_schedule WHERE schedule_id=1", Integer.class));
    assertEquals("3000.00", db.queryForObject("SELECT base_salary FROM edu_teacher_salary_config WHERE teacher_id=4", java.math.BigDecimal.class).toPlainString());
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='edu_teacher_salary_config' AND column_name IN('one_to_one_level','active_teacher_guard')", Integer.class));
    assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name IN('teacher_level','teacher_subject') AND column_type='varchar(20)' AND is_nullable='YES' AND column_default IS NULL", Integer.class));
    assertNull(db.queryForObject("SELECT teacher_level FROM sys_user WHERE user_id=4",String.class));
    assertEquals("system/oneToOne/index", db.queryForObject("SELECT component FROM sys_menu WHERE perms='system:oneToOne:list'", String.class));
    assertEquals(9, db.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE perms LIKE 'system:oneToOne:%'", Integer.class));
    assertEquals(9, db.queryForObject("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE rm.role_id=1 AND m.perms LIKE 'system:oneToOne:%'", Integer.class));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE rm.role_id=2 AND m.perms LIKE 'system:oneToOne:%'", Integer.class));
    seedLesson();
  }

  void seedLesson() {
    db.execute("INSERT INTO edu_student(student_id,parent_id,student_name) VALUES(1,2,'同名'),(2,2,'同名'),(3,3,'同名')");
    db.execute("INSERT INTO edu_lesson_package(package_id,package_name,subject_code,subject_name,total_units,price) VALUES(1,'数学十次','math','数学',10,1000.00)");
    db.execute("INSERT INTO edu_enrollment(enrollment_id,enrollment_code,schedule_id,parent_id,student_id,student_name,bill_type,lesson_package_id) VALUES(2,'PACKAGE',NULL,2,1,'同名','LESSON_PACKAGE',1)");
    db.execute("INSERT INTO edu_student_package(student_package_id,student_id,parent_id,package_id,enrollment_id,package_name_snapshot,subject_code,subject_name_snapshot,total_units,available_units,purchased_price,status,request_key,request_hash) VALUES(1,1,2,1,2,'数学十次','math','数学',10,0,1000.00,'PENDING_PAYMENT','BUY1',REPEAT('a',64))");
    db.execute("UPDATE edu_student_package SET available_units=10,status='ACTIVE',activated_time=NOW(3) WHERE student_package_id=1");
    db.execute("INSERT INTO edu_one_to_one_slot(slot_id,teacher_id,subject_code,subject_name_snapshot,classroom_id,start_time,end_time) VALUES(1,4,'math','数学',1,'2099-10-11 10:00:00','2099-10-11 12:00:00')");
  }

  void booking(String code, String state) {
    db.update("INSERT INTO edu_one_to_one_booking(booking_code,slot_id,student_id,parent_id,student_package_id,teacher_id,teacher_level_snapshot,subject_code,classroom_id,start_time_snapshot,end_time_snapshot,deducted_units,returned_units,status,request_key,request_hash) VALUES(?,1,1,2,1,4,'elite','math',1,'2099-10-11 10:00:00','2099-10-11 12:00:00',1,0,?,?,REPEAT('b',64))", code,state,code);
  }

  @Test @Order(2)
  void effectiveBookingGuardKeepsCompletedOccupiedAndAllowsRebookingAfterCancel() {
    booking("BOOK1", "BOOKED");
    assertThrows(DataIntegrityViolationException.class, () -> booking("BOOK2", "BOOKED"));
    db.execute("UPDATE edu_one_to_one_booking SET status='COMPLETED' WHERE booking_code='BOOK1'");
    assertThrows(DataIntegrityViolationException.class, () -> booking("BOOK3", "BOOKED"));
    db.execute("UPDATE edu_one_to_one_booking SET status='CANCELLED' WHERE booking_code='BOOK1'");
    booking("BOOK4", "BOOKED");
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM edu_one_to_one_booking WHERE active_slot_guard=1", Integer.class));
    checkRejected("UPDATE edu_one_to_one_booking SET returned_units=2 WHERE booking_code='BOOK4'");
    assertThrows(DataIntegrityViolationException.class, () -> db.execute("UPDATE edu_one_to_one_booking SET request_key='BOOK1' WHERE booking_code='BOOK4'"));
    assertThrows(Exception.class, () -> db.execute("DELETE FROM edu_one_to_one_booking WHERE booking_code='BOOK1'"));
  }

  @Test @Order(3)
  void rejectsInvalidMoneyBalancesStatesBillCombinationsAndBrokenOwnership() {
    for (String assignment : List.of("available_units=-1", "available_units=11", "status='CLOSED'", "status='PENDING_PAYMENT'", "purchased_price=-0.01", "status='UNKNOWN'")) {
      checkRejected("UPDATE edu_student_package SET " + assignment + " WHERE student_package_id=1");
    }
    for (String assignment : List.of("schedule_id=NULL")) {
      checkRejected("UPDATE edu_enrollment SET " + assignment + " WHERE enrollment_id=1");
    }
    for (String assignment : List.of("schedule_id=1")) {
      checkRejected("UPDATE edu_enrollment SET " + assignment + " WHERE enrollment_id=2");
    }
    for (String values : List.of("NULL,2,NULL,'COURSE',NULL", "1,2,NULL,'BAD',NULL", "1,2,NULL,'COURSE',1", "1,2,1,'LESSON_PACKAGE',1", "NULL,2,NULL,'LESSON_PACKAGE',1", "NULL,2,1,'LESSON_PACKAGE',NULL")) {
      checkRejected("INSERT INTO edu_enrollment(enrollment_code,schedule_id,parent_id,student_id,bill_type,lesson_package_id,student_name) VALUES(REPLACE(UUID(),'-','')," + values + ",'无效')");
    }
    assertThrows(Exception.class, () -> db.execute("UPDATE edu_enrollment SET bill_type='COURSE',schedule_id=1,lesson_package_id=NULL WHERE enrollment_id=2"));
    assertThrows(Exception.class, () -> db.execute("UPDATE edu_enrollment SET student_id=NULL WHERE enrollment_id=2"));
    assertThrows(DataIntegrityViolationException.class, () -> db.execute("UPDATE edu_student_package SET student_id=3 WHERE student_package_id=1"));
    assertThrows(DataIntegrityViolationException.class, () -> db.execute("UPDATE edu_one_to_one_booking SET student_id=2 WHERE booking_code='BOOK4'"));
    checkRejected("UPDATE edu_one_to_one_slot SET end_time='2099-10-11 11:00:00' WHERE slot_id=1");
    checkRejected("UPDATE edu_lesson_package SET total_units=0 WHERE package_id=1");
    checkRejected("UPDATE edu_one_to_one_booking SET teacher_level_snapshot='senior' WHERE booking_code='BOOK4'");
    db.execute("UPDATE edu_one_to_one_booking SET teacher_level_snapshot='senior',deducted_units=2 WHERE booking_code='BOOK4'");
    checkRejected("UPDATE edu_one_to_one_booking SET teacher_level_snapshot='elite' WHERE booking_code='BOOK4'");
    checkRejected("UPDATE edu_one_to_one_booking SET teacher_level_snapshot='SENIOR' WHERE booking_code='BOOK4'");
    checkRejected("UPDATE edu_one_to_one_booking SET teacher_level_snapshot='NORMAL' WHERE booking_code='BOOK4'");
  }

  @Test @Order(4)
  void stableChildIdentityAllowsSameNameSiblingsAndPreventsRenamingDuplicateCourse() {
    db.execute("INSERT INTO edu_enrollment(enrollment_code,schedule_id,parent_id,student_id,student_name) VALUES('CHILD1',1,2,1,'同名'),('CHILD2',1,2,2,'同名')");
    assertThrows(DataIntegrityViolationException.class, () -> db.execute("INSERT INTO edu_enrollment(enrollment_code,schedule_id,parent_id,student_id,student_name) VALUES('DUPLICATE',1,2,1,'改名')"));
    assertThrows(DataIntegrityViolationException.class, () -> db.execute("UPDATE edu_enrollment SET student_id=3 WHERE enrollment_code='HISTORY'"));
    db.execute("UPDATE edu_enrollment SET enrollment_status='已取消',student_id=1 WHERE enrollment_code='HISTORY'");
    assertEquals("历史学生", db.queryForObject("SELECT student_name FROM edu_enrollment WHERE enrollment_code='HISTORY'", String.class));
    assertThrows(Exception.class, () -> db.execute("UPDATE edu_enrollment SET student_id=2 WHERE enrollment_code='HISTORY'"));
  }

  @Test @Order(5)
  void unitEventsAreUniqueBalancedAppendOnlyAndCannotReferenceMissingFacts() {
    db.execute("INSERT INTO edu_lesson_unit_log(student_package_id,event_type,delta_units,before_units,after_units,event_key,actor_id) VALUES(1,'ACTIVATE',10,0,10,'ACTIVATE:1',1)");
    assertThrows(DataIntegrityViolationException.class, () -> db.execute("INSERT INTO edu_lesson_unit_log(student_package_id,event_type,delta_units,before_units,after_units,event_key,actor_id) VALUES(1,'ACTIVATE',10,0,10,'ACTIVATE:1',1)"));
    checkRejected("INSERT INTO edu_lesson_unit_log(student_package_id,event_type,delta_units,before_units,after_units,event_key,actor_id) VALUES(1,'BOOK',-1,10,10,'BAD',1)");
    assertThrows(DataIntegrityViolationException.class, () -> db.execute("INSERT INTO edu_lesson_unit_log(student_package_id,event_type,delta_units,before_units,after_units,event_key,actor_id) VALUES(99,'ACTIVATE',10,0,10,'ORPHAN',1)"));
    assertThrows(Exception.class, () -> db.execute("UPDATE edu_lesson_unit_log SET reason='rewrite' WHERE event_key='ACTIVATE:1'"));
    assertThrows(Exception.class, () -> db.execute("DELETE FROM edu_lesson_unit_log WHERE event_key='ACTIVATE:1'"));
  }

  @Test @Order(6)
  void rerunRefusesExistingColumnIndexCheckForeignKeyAndGeneratedConflicts() throws Exception {
    db.execute("ALTER TABLE edu_student MODIFY student_phone varchar(20) NULL");
    assertConflict("column", "student_phone");
    db.execute("ALTER TABLE edu_student MODIFY student_phone varchar(11) NULL");
    db.execute("ALTER TABLE edu_student_package MODIFY request_hash char(64) NULL");
    assertConflict("nullable", "request_hash");
    db.execute("ALTER TABLE edu_student_package MODIFY request_hash char(64) NOT NULL");
    db.execute("ALTER TABLE edu_one_to_one_slot ALTER COLUMN status SET DEFAULT 'CLOSED'");
    assertConflict("default", "status");
    db.execute("ALTER TABLE edu_one_to_one_slot ALTER COLUMN status SET DEFAULT 'OPEN'");
    db.execute("ALTER TABLE edu_one_to_one_booking DROP INDEX uk_lbooking_request, ADD UNIQUE KEY uk_lbooking_request(parent_id,booking_code)");
    assertConflict("index", "uk_lbooking_request");
    db.execute("ALTER TABLE edu_one_to_one_booking DROP INDEX uk_lbooking_request, ADD UNIQUE KEY uk_lbooking_request(parent_id,request_key)");
    db.execute("ALTER TABLE edu_lesson_package DROP CHECK ck_lpackage_rules, ADD CONSTRAINT ck_lpackage_rules CHECK(price>=0)");
    assertConflict("check", "ck_lpackage_rules");
    db.execute("ALTER TABLE edu_lesson_package DROP CHECK ck_lpackage_rules");
    migrateSuccessfully();
    db.execute("ALTER TABLE edu_one_to_one_slot DROP CHECK ck_lslot_rules");
    db.execute("ALTER TABLE edu_one_to_one_slot ADD CONSTRAINT ck_lslot_rules CHECK((end_time=start_time+INTERVAL 2 HOUR AND status IN ('OPEN','CLOSED')) OR version>=0)");
    assertConflict("check grouping", "ck_lslot_rules");
    db.execute("ALTER TABLE edu_one_to_one_slot DROP CHECK ck_lslot_rules");
    migrateSuccessfully();
    db.execute("ALTER TABLE edu_lesson_unit_log DROP FOREIGN KEY fk_llog_actor");
    db.execute("ALTER TABLE edu_lesson_unit_log ADD CONSTRAINT fk_llog_actor FOREIGN KEY(actor_id) REFERENCES sys_user(user_id) ON DELETE CASCADE");
    assertConflict("foreign", "fk_llog_actor");
    db.execute("ALTER TABLE edu_lesson_unit_log DROP FOREIGN KEY fk_llog_actor");
    migrateSuccessfully();
    db.execute("ALTER TABLE edu_one_to_one_booking MODIFY active_slot_guard tinyint GENERATED ALWAYS AS (CASE WHEN status='BOOKED' THEN 1 END) STORED");
    assertConflict("generated", "active_slot_guard");
  }

  void assertConflict(String kind, String name) throws Exception {
    Result result = migrate(MIGRATION);
    assertNotEquals(0, result.exit, "Must refuse incompatible " + kind);
    assertTrue(result.output.contains(name), result.output);
  }

  @Test @Order(7)
  void salaryDuplicatesDoNotBlockCanonicalGradeMigrationOrGetModified() throws Exception {
    resetFixture();
    db.execute("ALTER TABLE edu_teacher_salary_config DROP INDEX uk_teacher");
    db.execute("INSERT INTO edu_teacher_salary_config(teacher_id,teacher_name) VALUES(4,'重复配置')");
    migrateSuccessfully();
    migrateSuccessfully();
    assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM edu_teacher_salary_config WHERE teacher_id=4", Integer.class));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='edu_teacher_salary_config' AND index_name='uk_lteacher_active'",Integer.class));
  }

  @Test @Order(8)
  void collaboratorFieldsAreReusedWithoutGuessingOrRewritingAndIncompatibleStructureFails() throws Exception {
    resetFixture();
    db.execute("ALTER TABLE sys_user ADD teacher_level varchar(20) NULL, ADD teacher_subject varchar(20) NULL");
    db.execute("UPDATE sys_user SET teacher_level='senior',teacher_subject='数学' WHERE user_id=4");
    migrateSuccessfully();
    migrateSuccessfully();
    assertEquals("senior",db.queryForObject("SELECT teacher_level FROM sys_user WHERE user_id=4",String.class));
    assertEquals("数学",db.queryForObject("SELECT teacher_subject FROM sys_user WHERE user_id=4",String.class));
    assertNull(db.queryForObject("SELECT teacher_level FROM sys_user WHERE user_id=5",String.class));
    for(String column:List.of("teacher_level","teacher_subject")) {
      db.execute("ALTER TABLE sys_user MODIFY "+column+" varchar(30) NULL");
      assertConflict("collaborator field type",column);
      db.execute("ALTER TABLE sys_user MODIFY "+column+" varchar(20) NULL");
      db.execute("ALTER TABLE sys_user ALTER COLUMN "+column+" SET DEFAULT 'elite'");
      assertConflict("collaborator field default",column);
      db.execute("ALTER TABLE sys_user ALTER COLUMN "+column+" DROP DEFAULT");
    }
  }

  @Test @Order(11)
  void invalidExistingCanonicalGradeStopsBeforeBusinessDdlAndReportsTeacher() throws Exception {
    for(String invalid:List.of("", "NORMAL", "Senior", "senior ")) {
      resetFixture();
      db.execute("ALTER TABLE sys_user ADD teacher_level varchar(20) NULL");
      db.update("UPDATE sys_user SET teacher_level=? WHERE user_id=4",invalid);
      Result result=migrate(MIGRATION);
      assertNotEquals(0,result.exit);
      assertTrue(result.output.contains("teacher_level")&&result.output.contains("4"),result.output);
      assertEquals(invalid,db.queryForObject("SELECT teacher_level FROM sys_user WHERE user_id=4",String.class));
      assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='edu_student'",Integer.class));
      assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='teacher_subject'",Integer.class));
    }
  }

  @Test @Order(9)
  void rerunRejectsCheckLiteralWhitespaceCaseAndBackslashChanges() throws Exception {
    for (String literal : List.of("'O PEN'", "'open'", "'O\\\\PEN'")) {
      resetFixture();
      migrateSuccessfully();
      db.execute("ALTER TABLE edu_one_to_one_slot DROP CHECK ck_lslot_rules");
      db.execute("ALTER TABLE edu_one_to_one_slot ADD CONSTRAINT ck_lslot_rules CHECK(end_time=start_time+INTERVAL 2 HOUR AND status IN (" + literal + ",'CLOSED') AND version>=0)");
      assertConflict("quoted literal " + literal, "ck_lslot_rules");
      db.execute("ALTER TABLE edu_one_to_one_slot DROP CHECK ck_lslot_rules");
      migrateSuccessfully();
      migrateSuccessfully();
    }
  }

  @Test @Order(10)
  void rerunRejectsGeneratedLiteralWhitespaceCaseAndBackslashChanges() throws Exception {
    for (String literal : List.of("'B OOKED'", "'booked'", "'B\\\\OOKED'")) {
      resetFixture();
      migrateSuccessfully();
      db.execute("ALTER TABLE edu_one_to_one_booking MODIFY active_slot_guard tinyint GENERATED ALWAYS AS (CASE WHEN status IN (" + literal + ",'COMPLETED') THEN 1 END) STORED");
      assertConflict("quoted generated literal " + literal, "active_slot_guard");
      db.execute("ALTER TABLE edu_one_to_one_booking MODIFY active_slot_guard tinyint GENERATED ALWAYS AS (CASE WHEN status IN ('BOOKED','COMPLETED') THEN 1 END) STORED");
      migrateSuccessfully();
      migrateSuccessfully();
    }
  }
}

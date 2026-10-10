package com.ruoyi.system.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.ruoyi.system.shop.ShopRepository;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TuitionMysqlTest {
  static final String SCHEMA = "codex_tuition_finance_test_20261007";
  ShopRepository db;
  Object tuition, coupons;
  Path privateRoot;
  FinancePrivateStorage storage;
  TuitionReceiptService receiptService;

  @BeforeAll
  void setup() throws Exception {
    Assumptions.assumeTrue(
        Boolean.getBoolean("tuition.mysql"),
        "Enable isolated MySQL suite with -Dtuition.mysql=true");
    try (Connection c =
        DriverManager.getConnection(
            "jdbc:mysql://127.0.0.1:3306/?serverTimezone=Asia/Shanghai", "root", "")) {
      c.createStatement().execute("DROP DATABASE IF EXISTS " + SCHEMA);
      c.createStatement().execute("CREATE DATABASE " + SCHEMA + " CHARACTER SET utf8mb4");
    }
    db =
        new ShopRepository(
            new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:3306/" + SCHEMA + "?serverTimezone=Asia/Shanghai",
                "root",
                ""));
    db.jdbc()
        .execute(
            "CREATE TABLE sys_user(user_id bigint PRIMARY KEY,user_name varchar(64),nick_name"
                + " varchar(64),status char(1) DEFAULT '0',del_flag char(1) DEFAULT '0')");
    db.jdbc()
        .execute(
            "INSERT INTO sys_user"
                + " VALUES(1,'admin','经办','0','0'),(2,'parent','家长','0','0'),(3,'other','其他家长','0','0'),(4,'checker','复核','0','0')");
    String schema = Files.readString(Path.of("../../database/schema.sql"));
    for (String table :
        List.of(
            "sys_role",
            "sys_role_menu",
            "sys_menu",
            "sys_config",
            "edu_classroom",
            "edu_course_schedule",
            "edu_enrollment",
            "edu_attendance")) {
      Matcher m =
          Pattern.compile("CREATE TABLE `" + table + "` \\(.*?\\) ENGINE=.*?;", Pattern.DOTALL)
              .matcher(schema);
      assertTrue(m.find());
      db.jdbc().execute(m.group());
    }
    db.jdbc().execute("CREATE TABLE sys_user_role(user_id bigint,role_id bigint)");
    db.jdbc()
        .execute(
            "INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status)"
                + " VALUES(1,'管理员','admin',1,'0'),(2,'家长','parent',2,'0')");
    db.jdbc().execute("INSERT INTO sys_user_role VALUES(1,1),(4,1),(2,2),(3,2)");
    db.jdbc()
        .execute(
            "INSERT INTO edu_classroom(classroom_id,classroom_code,campus_name,classroom_name)"
                + " VALUES(1,'TEST','测试校区','A')");
    db.jdbc()
        .execute(
            "INSERT INTO"
                + " edu_course_schedule(schedule_id,schedule_code,classroom_id,term_name,period_name,time_slot,grade_name,subject_name,course_class_name,enrolled_count)"
                + " VALUES(1,'TEST',1,'秋季','周日','上午','五年级','数学','测试课程',100)");
    db.jdbc()
        .execute(
            "INSERT INTO"
                + " edu_enrollment(enrollment_code,schedule_id,parent_id,student_name,pay_status)"
                + " VALUES('HISTORY',1,2,'历史学生','已支付')");
    // Simulate interruption immediately after the first bill-status ALTER.
    db.jdbc()
        .execute(
            "ALTER TABLE edu_enrollment ADD billing_status varchar(20) NOT NULL DEFAULT"
                + " 'HISTORY_PENDING'");
    assertEquals(
        "HISTORY_PENDING",
        db.one("SELECT billing_status FROM edu_enrollment WHERE enrollment_code='HISTORY'")
            .get("billingStatus"));
    migrate();
    migrate();
    privateRoot = Files.createTempDirectory(Path.of("/private/tmp"), "codex-tuition-finance-test-");
    new com.ruoyi.common.config.RuoYiConfig().setProfile(privateRoot.resolve("public").toString());
    storage = new FinancePrivateStorage(privateRoot.resolve("private").toString());
    receiptService = new TuitionReceiptService(db, storage);
  }

  void migrate() throws Exception {
    Process p =
        new ProcessBuilder(
                "/opt/homebrew/bin/mysql", "--protocol=TCP", "-h127.0.0.1", "-uroot", SCHEMA)
            .redirectErrorStream(true)
            .start();
    try (var out = p.getOutputStream()) {
      out.write(
          Files.readAllBytes(Path.of("../../database/migrations/20261007_tuition_finance.sql")));
      out.write(Files.readAllBytes(Path.of("../../database/migrations/20261010_tuition_refund_single_admin.sql")));
    }
    String output = new String(p.getInputStream().readAllBytes());
    assertEquals(0, p.waitFor(), output);
  }

  @AfterAll
  void cleanup() throws Exception {
    if (db != null)
      try (Connection c = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "")) {
        c.createStatement().execute("DROP DATABASE IF EXISTS " + SCHEMA);
      }
    if (privateRoot != null)
      try (var paths = Files.walk(privateRoot)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
      }
  }

  Object service(String name) throws Exception {
    try {
      Class<?> c = Class.forName("com.ruoyi.system.finance." + name);
      if (name.equals("CouponService"))
        return c.getConstructor(ShopRepository.class).newInstance(db);
      return Arrays.stream(c.getConstructors())
          .filter(x -> x.getParameterCount() == 5 && x.getParameterTypes()[4] == boolean.class)
          .findFirst()
          .orElseThrow()
          .newInstance(db, storage, receiptService, service("CouponService"), true);
    } catch (ClassNotFoundException e) {
      fail("Tuition payment/refund/coupon workflow is not implemented");
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> call(Object s, String name, Object... args) throws Exception {
    Method method =
        Arrays.stream(s.getClass().getMethods())
            .filter(m -> m.getName().equals(name) && m.getParameterCount() == args.length)
            .findFirst()
            .orElseThrow();
    try {
      return (Map<String, Object>) method.invoke(s, args);
    } catch (InvocationTargetException e) {
      throw (Exception) e.getCause();
    }
  }

  long fresh() {
    String code = UUID.randomUUID().toString().substring(0, 30);
    return db.insert(
        "edu_enrollment",
        Map.of(
            "enrollment_code",
            code,
            "schedule_id",
            1,
            "parent_id",
            2,
            "student_name",
            code,
            "enrollment_status",
            "报名成功"));
  }

  Map<String, Object> priceBody(String mode) {
    return Map.of("originalAmount", "1000.00", "financialVersion", 0, "financeMode", mode);
  }

  Map<String, Object> payBody(int version, String key) {
    return Map.of(
        "channel",
        "MOCK",
        "expectedPayableAmount",
        "1000.00",
        "financialVersion",
        version,
        "idempotencyKey",
        key);
  }

  long lid(Map<String, Object> row, String key) {
    return Long.parseLong(String.valueOf(row.get(key)));
  }

  String proof(long actor, String type, long business) throws Exception {
    return storage
        .uploadEvidence(
            actor,
            type,
            business,
            new FinanceFilesTest.FilePart("proof.png", FinanceFilesTest.png()))
        .get("evidenceKey")
        .toString();
  }

  Map<String, Object> templateBody(int quantity) {
    return Map.of(
        "couponName",
        "测试券",
        "discountAmount",
        "100",
        "minSpendAmount",
        "500",
        "validFrom",
        "2020-01-01 00:00:00",
        "validUntil",
        "2099-01-01 00:00:00",
        "totalQuantity",
        quantity,
        "templateStatus",
        "ACTIVE");
  }

  @Test
  void historyIsUnknownAndCannotBeCollectedWithoutDedicatedVerification() throws Exception {
    var history = db.one("SELECT * FROM edu_enrollment WHERE enrollment_code='HISTORY'");
    assertEquals("HISTORY_PENDING", history.get("billingStatus"));
    assertNull(history.get("originalAmount"));
    assertEquals("已支付", history.get("payStatus"));
    var s = service("TuitionService");
    assertThrows(
        Exception.class,
        () -> call(s, "price", lid(history, "enrollmentId"), 1L, priceBody("REAL")));
  }

  @Test
  void interruptedMigrationKeepsOldPaidUnknownAndOnlyNewRowsDefaultToPendingPrice() {
    var old = db.one("SELECT * FROM edu_enrollment WHERE enrollment_code='HISTORY'");
    assertEquals("已支付", old.get("payStatus"));
    assertEquals("HISTORY_PENDING", old.get("billingStatus"));
    assertNull(old.get("originalAmount"));
    assertNull(old.get("discountAmount"));
    assertNull(old.get("payableAmount"));
    assertEquals(
        "PENDING_PRICE",
        db.one("SELECT billing_status FROM edu_enrollment WHERE enrollment_id=?", fresh())
            .get("billingStatus"));
  }

  @Test
  void successfulPaymentIsIdempotentImmutableAndOwned() throws Exception {
    var s = service("TuitionService");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("MOCK"));
    assertThrows(
        Exception.class,
        () ->
            call(
                s,
                "createPayment",
                id,
                2L,
                false,
                Map.of(
                    "channel",
                    "MOCK",
                    "expectedPayableAmount",
                    "1.00",
                    "financialVersion",
                    1,
                    "idempotencyKey",
                    "bad" + id)));
    var p = call(s, "createPayment", id, 2L, false, payBody(1, "PAY" + id));
    assertEquals(
        p.get("paymentId"),
        call(s, "createPayment", id, 2L, false, payBody(1, "PAY" + id)).get("paymentId"));
    call(s, "confirmPayment", lid(p, "paymentId"), 2L, false, Map.of());
    call(s, "confirmPayment", lid(p, "paymentId"), 2L, false, Map.of());
    assertEquals(
        1,
        db.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM edu_tuition_receipt WHERE payment_id=?",
                Integer.class,
                lid(p, "paymentId")));
    assertEquals("1000.00", call(s, "bill", id, 2L, false).get("receivedAmount"));
    assertThrows(Exception.class, () -> call(s, "bill", id, 3L, false));
    assertThrows(
        Exception.class,
        () ->
            call(
                s,
                "price",
                id,
                1L,
                Map.of("originalAmount", "500", "financialVersion", 2, "financeMode", "REAL")));
  }

  @Test
  void partialRefundsReserveUnknownAndReleaseWithdrawalCapacityExactlyOnce() throws Exception {
    var s = service("TuitionService");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("MOCK"));
    var p = call(s, "createPayment", id, 2L, false, payBody(1, "PR" + id));
    long payment = lid(p, "paymentId");
    call(s, "confirmPayment", payment, 2L, false, Map.of());
    var r =
        call(
            s,
            "applyRefund",
            2L,
            false,
            Map.of(
                "paymentId",
                payment,
                "refundKind",
                "PARTIAL",
                "requestedAmount",
                "300",
                "reason",
                "补偿",
                "idempotencyKey",
                "R1" + id));
    long rid = lid(r, "refundId");
    assertThrows(
        Exception.class,
        () ->
            call(
                s,
                "applyRefund",
                2L,
                false,
                Map.of(
                    "paymentId",
                    payment,
                    "refundKind",
                    "PARTIAL",
                    "requestedAmount",
                    "300",
                    "reason",
                    "重复",
                    "idempotencyKey",
                    "R2" + id)));
    final long firstRefund = rid;
    assertThrows(
        Exception.class,
        () ->
            call(
                s,
                "reviewRefund",
                firstRefund,
                2L,
                Map.of("approved", true, "approvedAmount", "300")));
    call(s, "reviewRefund", rid, 4L, Map.of("approved", true, "approvedAmount", "300"));
    call(s, "executeRefund", rid, 1L);
    var bill = call(s, "bill", id, 2L, false);
    assertEquals("700.00", bill.get("netAmount"));
    assertEquals("0.00", bill.get("dueAmount"));
    r =
        call(
            s,
            "applyRefund",
            2L,
            false,
            Map.of(
                "paymentId",
                payment,
                "refundKind",
                "WITHDRAWAL",
                "requestedAmount",
                "700",
                "reason",
                "退课",
                "idempotencyKey",
                "R3" + id));
    rid = lid(r, "refundId");
    call(s, "reviewRefund", rid, 4L, Map.of("approved", true, "approvedAmount", "700"));
    int before =
        db.jdbc()
            .queryForObject(
                "SELECT enrolled_count FROM edu_course_schedule WHERE schedule_id=1",
                Integer.class);
    call(s, "executeRefund", rid, 1L);
    call(s, "executeRefund", rid, 1L);
    assertEquals(
        before - 1,
        db.jdbc()
            .queryForObject(
                "SELECT enrolled_count FROM edu_course_schedule WHERE schedule_id=1",
                Integer.class));
    assertEquals("已取消", call(s, "bill", id, 2L, false).get("enrollmentStatus"));
  }

  @Test
  void cancellationInvalidatesOnlyUnusedAttendance() throws Exception {
    var s = service("TuitionService");
    long unused = fresh(), attended = fresh();
    db.jdbc().update(
        "INSERT INTO edu_attendance(attendance_code,schedule_id,enrollment_id,parent_id,student_name) VALUES(?,1,?,2,'学生')",
        "unused" + unused, unused);
    db.jdbc().update(
        "INSERT INTO edu_attendance(attendance_code,schedule_id,enrollment_id,parent_id,student_name,attendance_status,attended_time,confirm_by,confirm_name) VALUES(?,1,?,2,'学生','1',NOW(),1,'经办')",
        "attended" + attended, attended);
    int before = db.jdbc().queryForObject("SELECT enrolled_count FROM edu_course_schedule WHERE schedule_id=1", Integer.class);
    call(s, "cancelEnrollment", unused, 2L, false);
    call(s, "cancelEnrollment", unused, 2L, false);
    call(s, "cancelEnrollment", attended, 2L, false);
    assertEquals("2", db.one("SELECT attendance_status FROM edu_attendance WHERE enrollment_id=?", unused).get("attendanceStatus"));
    assertEquals("1", db.one("SELECT attendance_status FROM edu_attendance WHERE enrollment_id=?", attended).get("attendanceStatus"));
    assertEquals(before - 2, db.jdbc().queryForObject("SELECT enrolled_count FROM edu_course_schedule WHERE schedule_id=1", Integer.class));
  }

  @Test
  void concurrentPaymentCreationAllowsOnlyOneEffectiveAttempt() throws Exception {
    var s = service("TuitionService");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("MOCK"));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> jobs = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        String key = "C" + i + id;
        jobs.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    call(s, "createPayment", id, 2L, false, payBody(1, key));
                    return true;
                  } catch (Exception e) {
                    return false;
                  }
                }));
      }
      start.countDown();
      int successes = 0;
      for (var j : jobs) if (j.get()) successes++;
      assertEquals(1, successes);
      assertEquals(
          1,
          db.jdbc()
              .queryForObject(
                  "SELECT COUNT(*) FROM edu_tuition_payment WHERE enrollment_id=?",
                  Integer.class,
                  id));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void offlineMoneyStaysPendingUntilEvidenceConfirmationAndUnknownRefundKeepsReservation()
      throws Exception {
    var s = service("TuitionService");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("REAL"));
    String evidence = proof(1, "PAYMENT", id);
    var b =
        new HashMap<String, Object>(
            Map.of(
                "channel",
                "BANK_TRANSFER",
                "expectedPayableAmount",
                "1000.00",
                "financialVersion",
                1,
                "idempotencyKey",
                "OFF" + id,
                "payerName",
                "测试付款人",
                "providerTradeNo",
                "BANK-" + id,
                "paidTime",
                "2025-10-07 12:00:00"));
    assertThrows(Exception.class, () -> call(s, "createPayment", id, 2L, false, b));
    assertThrows(Exception.class, () -> call(s, "createPayment", id, 1L, true, b));
    b.put("evidenceKey", evidence);
    var p = call(s, "createPayment", id, 1L, true, b);
    long payment = lid(p, "paymentId");
    assertEquals("PENDING", p.get("paymentStatus"));
    assertEquals("0.00", call(s, "bill", id, 2L, false).get("receivedAmount"));
    assertThrows(Exception.class, () -> call(s, "cancelEnrollment", id, 2L, false));
    assertThrows(Exception.class, () -> call(s, "confirmPayment", payment, 2L, false, Map.of()));
    call(s, "confirmPayment", payment, 4L, true, Map.of());
    var r =
        call(
            s,
            "applyRefund",
            1L,
            true,
            Map.of(
                "paymentId",
                payment,
                "refundKind",
                "PARTIAL",
                "requestedAmount",
                "300",
                "reason",
                "测试退款",
                "idempotencyKey",
                "OFFR" + id));
    long refund = lid(r, "refundId");
    var reviewed = call(s, "reviewRefund", refund, 1L, Map.of("approved", true, "approvedAmount", "300"));
    assertEquals("APPROVED", reviewed.get("refundStatus"));
    assertEquals(1L, lid(reviewed, "reviewerId"));
    assertThrows(Exception.class, () -> call(s, "cancelRefund", refund, 2L));
    call(s, "executeRefund", refund, 1L);
    assertThrows(Exception.class, () -> call(s, "cancelRefund", refund, 1L, true));
    call(s, "confirmRefund", refund, 1L, Map.of("outcome", "UNKNOWN", "reason", "转账结果待查"));
    assertThrows(Exception.class, () -> call(s, "cancelRefund", refund, 2L));
    assertEquals("700.00", call(s, "bill", id, 2L, false).get("refundableAmount"));
    assertEquals("1000.00", call(s, "bill", id, 2L, false).get("netAmount"));
    String rp = proof(1, "REFUND", refund);
    call(
        s,
        "confirmRefund",
        refund,
        1L,
        Map.of(
            "evidenceKey",
            rp,
            "providerRefundNo",
            "BANK-R-" + refund,
            "completedTime",
            "2025-10-07 13:00:00"));
    assertEquals("700.00", call(s, "bill", id, 2L, false).get("netAmount"));
    assertThrows(Exception.class, () -> call(s, "cancelRefund", refund, 1L, true));
    assertThrows(Exception.class, () -> db.jdbc().update(
        "UPDATE edu_tuition_refund SET approved_amount=1 WHERE refund_id=?", refund));
  }

  @Test
  void parentCanWithdrawAdminApplicationAndAdminCanWithdrawOwnPendingApplication() throws Exception {
    var s = service("TuitionService");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("MOCK"));
    var payment = call(s, "createPayment", id, 2L, false, payBody(1, "CANCEL" + id));
    long paymentId = lid(payment, "paymentId");
    call(s, "confirmPayment", paymentId, 2L, false, Map.of());
    for (boolean adminCancel : List.of(false, true)) {
      var refund = call(s, "applyRefund", 1L, true, Map.of(
          "paymentId", paymentId, "refundKind", "PARTIAL", "requestedAmount", "300",
          "reason", "撤回测试", "idempotencyKey", "WITHDRAW" + id + adminCancel));
      long refundId = lid(refund, "refundId");
      assertThrows(Exception.class, () -> call(s, "cancelRefund", refundId, 3L));
      assertThrows(Exception.class, () -> call(s, "cancelRefund", refundId, 4L, true));
      var cancelled = adminCancel
          ? call(s, "cancelRefund", refundId, 1L, true)
          : call(s, "cancelRefund", refundId, 2L);
      assertEquals("CANCELLED", cancelled.get("refundStatus"));
      assertEquals(1L, lid(cancelled, "applicantId"));
      assertEquals("1000.00", call(s, "bill", id, 2L, false).get("refundableAmount"));
      assertThrows(Exception.class, () -> call(s, "reviewRefund", refundId, 1L,
          Map.of("approved", true, "approvedAmount", "300")));
    }
  }

  @Test
  void couponBatchIsAtomicIdempotentFrozenAndOneCouponCannotLockTwoBills() throws Exception {
    var c = service("CouponService");
    long template = lid(call(c, "saveTemplate", 1L, null, templateBody(2)), "templateId");
    assertThrows(
        Exception.class,
        () ->
            call(
                c,
                "issue",
                template,
                1L,
                Map.of(
                    "parentIds",
                    List.of(2, 999),
                    "reason",
                    "活动",
                    "idempotencyKey",
                    "invalid" + template)));
    assertEquals(
        0,
        db.jdbc()
            .queryForObject(
                "SELECT issued_quantity FROM edu_coupon_template WHERE template_id=?",
                Integer.class,
                template));
    Map<String, Object> grant =
        Map.of("parentIds", List.of(2, 3), "reason", "活动", "idempotencyKey", "batch" + template);
    var issued = call(c, "issue", template, 1L, grant);
    call(c, "issue", template, 1L, grant);
    assertEquals(
        2,
        db.jdbc()
            .queryForObject(
                "SELECT issued_quantity FROM edu_coupon_template WHERE template_id=?",
                Integer.class,
                template));
    assertThrows(
        Exception.class,
        () ->
            call(
                c,
                "issue",
                template,
                1L,
                Map.of(
                    "parentIds",
                    List.of(2),
                    "reason",
                    "活动",
                    "idempotencyKey",
                    "batch" + template)));
    assertThrows(
        Exception.class,
        () -> call(c, "saveTemplate", 1L, template, Map.of("discountAmount", "999")));
    call(c, "saveTemplate", 1L, template, Map.of("templateStatus", "PAUSED"));
    long coupon =
        Long.parseLong(
            db.one(
                    "SELECT user_coupon_id FROM edu_user_coupon WHERE template_id=? AND"
                        + " parent_id=2",
                    template)
                .get("userCouponId")
                .toString());
    var s = service("TuitionService");
    long first = fresh(), second = fresh();
    call(s, "price", first, 1L, priceBody("MOCK"));
    call(s, "price", second, 1L, priceBody("MOCK"));
    assertEquals(
        "900.00",
        call(s, "quote", first, 2L, false, Map.of("userCouponId", coupon)).get("payableAmount"));
    var pay =
        Map.of(
            "channel",
            "MOCK",
            "userCouponId",
            coupon,
            "expectedPayableAmount",
            "900.00",
            "financialVersion",
            1,
            "idempotencyKey",
            "coupon" + first);
    var payment = call(s, "createPayment", first, 2L, false, pay);
    assertThrows(
        Exception.class,
        () ->
            call(
                s,
                "createPayment",
                second,
                2L,
                false,
                Map.of(
                    "channel",
                    "MOCK",
                    "userCouponId",
                    coupon,
                    "expectedPayableAmount",
                    "900.00",
                    "financialVersion",
                    1,
                    "idempotencyKey",
                    "coupon" + second)));
    call(s, "closePayment", lid(payment, "paymentId"), 1L, Map.of("reason", "付款取消"));
    assertEquals(
        "AVAILABLE",
        db.one("SELECT coupon_status FROM edu_user_coupon WHERE user_coupon_id=?", coupon)
            .get("couponStatus"));
    assertEquals(
        2,
        ((Number)
                call(c, "grants", 1L, true, Map.of("templateId", String.valueOf(template)))
                    .get("total"))
            .intValue());
  }

  @Test
  void zeroCouponWaiverCannotRefundCashAndCanSafelyWithdraw() throws Exception {
    var c = service("CouponService");
    var rules = new HashMap<String, Object>(templateBody(1));
    rules.put("discountAmount", "1500");
    long template = lid(call(c, "saveTemplate", 1L, null, rules), "templateId");
    call(
        c,
        "issue",
        template,
        1L,
        Map.of("parentIds", List.of(2), "reason", "减免", "idempotencyKey", "free" + template));
    long coupon =
        Long.parseLong(
            db.one("SELECT user_coupon_id FROM edu_user_coupon WHERE template_id=?", template)
                .get("userCouponId")
                .toString());
    var s = service("TuitionService");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("REAL"));
    var p =
        call(
            s,
            "createPayment",
            id,
            2L,
            false,
            Map.of(
                "channel",
                "FREE",
                "userCouponId",
                coupon,
                "expectedPayableAmount",
                "0.00",
                "financialVersion",
                1,
                "idempotencyKey",
                "free" + id));
    assertEquals("SUCCESS", p.get("paymentStatus"));
    assertEquals("已减免", call(s, "bill", id, 2L, false).get("payStatus"));
    assertThrows(
        Exception.class,
        () ->
            call(
                s,
                "applyRefund",
                2L,
                false,
                Map.of(
                    "paymentId",
                    p.get("paymentId"),
                    "refundKind",
                    "PARTIAL",
                    "requestedAmount",
                    "1",
                    "reason",
                    "不能现金退款",
                    "idempotencyKey",
                    "free-refund" + id)));
    call(s, "cancelEnrollment", id, 2L, false);
    assertEquals(
        "USED",
        db.one("SELECT coupon_status FROM edu_user_coupon WHERE user_coupon_id=?", coupon)
            .get("couponStatus"));
  }

  @Test
  void receiptFailurePreservesPaymentAndRetryUsesSameSnapshotAndNumber() throws Exception {
    var failing =
        new FinancePrivateStorage(privateRoot.resolve("private").toString()) {
          @Override
          public String publishReceipt(byte[] bytes) throws java.io.IOException {
            throw new java.io.IOException("test disk failure");
          }
        };
    var rs = new TuitionReceiptService(db, failing);
    var s = new TuitionService(db, storage, rs, new CouponService(db), true);
    long id = fresh();
    s.price(id, 1, priceBody("MOCK"));
    var p = s.createPayment(id, 2, false, payBody(1, "PDF" + id));
    s.confirmPayment(lid(p, "paymentId"), 2, false, Map.of());
    var r = db.one("SELECT * FROM edu_tuition_receipt WHERE payment_id=?", p.get("paymentId"));
    assertEquals("FAILED", r.get("fileStatus"));
    assertEquals("SUCCESS", s.payment(lid(p, "paymentId"), 2, false).get("paymentStatus"));
    long receipt = lid(r, "receiptId");
    receiptService.retry(receipt, 1);
    var ready = receiptService.receipt(receipt, 2, false);
    assertEquals("READY", ready.get("fileStatus"));
    assertEquals(r.get("receiptNo"), ready.get("receiptNo"));
    assertEquals(
        r.get("contentSnapshot"),
        db.one("SELECT content_snapshot FROM edu_tuition_receipt WHERE receipt_id=?", receipt)
            .get("contentSnapshot"));
    assertThrows(Exception.class, () -> receiptService.download(receipt, 3, false));
    assertTrue(Files.isRegularFile(receiptService.download(receipt, 2, false)));
    String originalTitle =
        com.alibaba
            .fastjson2
            .JSON
            .parseObject(String.valueOf(r.get("contentSnapshot")))
            .getString("institutionTitle");
    db.jdbc()
        .update(
            "INSERT INTO sys_config(config_name,config_key,config_value)"
                + " VALUES('测试机构','finance.institutionTitle','测试新抬头')");
    var replacement = receiptService.replace(receipt, 1, "测试更正");
    assertEquals(
        "测试新抬头",
        com.alibaba
            .fastjson2
            .JSON
            .parseObject(
                String.valueOf(
                    db.one(
                            "SELECT content_snapshot FROM edu_tuition_receipt WHERE receipt_id=?",
                            replacement.get("receiptId"))
                        .get("contentSnapshot")))
            .getString("institutionTitle"));
    assertEquals(
        originalTitle,
        com.alibaba
            .fastjson2
            .JSON
            .parseObject(
                String.valueOf(
                    db.one(
                            "SELECT content_snapshot FROM edu_tuition_receipt WHERE receipt_id=?",
                            receipt)
                        .get("contentSnapshot")))
            .getString("institutionTitle"));
    db.jdbc().update("DELETE FROM sys_config WHERE config_key='finance.institutionTitle'");
    assertEquals("1000.00", replacement.get("amount"));
    assertThrows(Exception.class, () -> receiptService.download(receipt, 2, false));
    assertEquals("1000.00", s.bill(id, 2, false).get("receivedAmount"));
  }

  @Test
  void productionRejectsExistingMockBillsAndRefundDetailsExposeReservation() throws Exception {
    var s = service("TuitionService");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("MOCK"));
    var prod = new TuitionService(db, storage, receiptService, new CouponService(db), false);
    assertThrows(Exception.class, () -> prod.bill(id, 2, false));
    assertThrows(Exception.class, () -> prod.createPayment(id, 2, false, payBody(1, "PROD" + id)));
    var p = call(s, "createPayment", id, 2L, false, payBody(1, "MD" + id));
    call(s, "confirmPayment", lid(p, "paymentId"), 2L, false, Map.of());
    var r =
        call(
            s,
            "applyRefund",
            2L,
            false,
            Map.of(
                "paymentId",
                p.get("paymentId"),
                "refundKind",
                "PARTIAL",
                "requestedAmount",
                "300",
                "reason",
                "审核展示",
                "idempotencyKey",
                "MD-r" + id));
    assertEquals(true, r.get("mockAllowed"));
    assertEquals("700.00", r.get("refundableAmount"));
  }

  @Test
  void legacyStatesAreNormalizedAndHistoryVerificationRequiresChronologicalKnownMoney()
      throws Exception {
    var s = service("TuitionService");
    long id = fresh();
    db.jdbc()
        .update(
            "UPDATE edu_enrollment SET enrollment_status='1',pay_status='0' WHERE enrollment_id=?",
            id);
    assertEquals("报名成功", call(s, "bill", id, 2L, false).get("enrollmentStatus"));
    assertEquals("未支付", call(s, "bill", id, 2L, false).get("payStatus"));
    long history = fresh();
    db.jdbc()
        .update(
            "UPDATE edu_enrollment SET billing_status='HISTORY_PENDING',pay_status='已退款' WHERE"
                + " enrollment_id=?",
            history);
    String evidence = proof(1, "HISTORY", history);
    var b =
        new HashMap<String, Object>(
            Map.of(
                "originalAmount",
                "1000",
                "historyPaidAmount",
                "900",
                "historyRefundedAmount",
                "300",
                "paidTime",
                "2025-10-07 12:00:00",
                "refundedTime",
                "2025-10-07 11:00:00",
                "financialVersion",
                0,
                "reason",
                "历史收退核验",
                "evidenceKey",
                evidence));
    assertThrows(Exception.class, () -> call(s, "historyVerify", history, 1L, b));
    b.put("refundedTime", "2025-10-07 13:00:00");
    var verified = call(s, "historyVerify", history, 1L, b);
    assertEquals("900.00", verified.get("receivedAmount"));
    assertEquals("300.00", verified.get("refundedAmount"));
    assertEquals("0.00", verified.get("dueAmount"));
    assertEquals("600.00", verified.get("refundableAmount"));
    assertEquals(
        "HISTORY_VERIFIED",
        db.one("SELECT record_source FROM edu_tuition_payment WHERE enrollment_id=?", history)
            .get("recordSource"));
    assertThrows(Exception.class, () -> call(s, "historyVerify", history, 1L, b));
    long unpaid = fresh();
    db.jdbc()
        .update(
            "UPDATE edu_enrollment SET billing_status='HISTORY_PENDING' WHERE enrollment_id=?",
            unpaid);
    var ub =
        Map.of(
            "originalAmount",
            "800",
            "historyPaidAmount",
            "0",
            "historyRefundedAmount",
            "0",
            "financialVersion",
            0,
            "reason",
            "凭据明确未支付",
            "evidenceKey",
            proof(1, "HISTORY", unpaid));
    assertEquals("800.00", call(s, "historyVerify", unpaid, 1L, ub).get("dueAmount"));
    assertEquals(
        0,
        db.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM edu_tuition_payment WHERE enrollment_id=?",
                Integer.class,
                unpaid));
    assertNotNull(
        db.one(
                "SELECT history_verification_snapshot FROM edu_enrollment WHERE enrollment_id=?",
                unpaid)
            .get("historyVerificationSnapshot"));
  }

  @Test
  void databaseRejectsMutationOfSuccessfulCashFactsAndMigrationConflictingTypes() throws Exception {
    var s = service("TuitionService");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("MOCK"));
    var p = call(s, "createPayment", id, 2L, false, payBody(1, "IMM" + id));
    call(s, "confirmPayment", lid(p, "paymentId"), 2L, false, Map.of());
    assertThrows(
        Exception.class,
        () ->
            db.jdbc()
                .update(
                    "UPDATE edu_tuition_payment SET amount=1 WHERE payment_id=?",
                    p.get("paymentId")));
    assertThrows(
        Exception.class,
        () ->
            db.jdbc()
                .update(
                    "UPDATE edu_tuition_receipt SET content_snapshot=JSON_OBJECT('amount','1.00')"
                        + " WHERE payment_id=?",
                    p.get("paymentId")));
    assertThrows(
        Exception.class,
        () ->
            db.jdbc()
                .update("UPDATE edu_enrollment SET student_name='换学生' WHERE enrollment_id=?", id));
    db.jdbc().execute("ALTER TABLE edu_enrollment MODIFY billing_close_reason varchar(254) NULL");
    try {
      assertThrows(AssertionError.class, this::migrate);
    } finally {
      db.jdbc().execute("ALTER TABLE edu_enrollment MODIFY billing_close_reason varchar(255) NULL");
    }
    migrate();
  }

  @Test
  void summaryDefaultsToRealAndUsesCashOccurrenceDatesInsteadOfEnrollmentCreation()
      throws Exception {
    var s = service("TuitionService");
    assertTrue(
        Arrays.stream(s.getClass().getMethods()).anyMatch(m -> m.getName().equals("summary")),
        "Finance summary is not implemented");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("REAL"));
    var summaryBefore = call(s, "summary", 1L, Map.of("enrollmentId", String.valueOf(id)));
    assertEquals("1000.00", summaryBefore.get("dueAmount"));
    String evidence = proof(1, "PAYMENT", id);
    var p =
        call(
            s,
            "createPayment",
            id,
            1L,
            true,
            Map.of(
                "channel",
                "CASH",
                "expectedPayableAmount",
                "1000.00",
                "financialVersion",
                1,
                "idempotencyKey",
                "SUM" + id,
                "paidTime",
                "2025-01-01 12:00:00",
                "evidenceKey",
                evidence));
    call(s, "confirmPayment", lid(p, "paymentId"), 1L, true, Map.of());
    var summary = call(s, "summary", 1L, Map.of("enrollmentId", String.valueOf(id)));
    assertEquals("1000.00", summary.get("receivedAmount"));
    assertEquals("0.00", summary.get("todayReceivedAmount"));
    assertEquals("0.00", summary.get("dueAmount"));
    long mock = fresh();
    call(s, "price", mock, 1L, priceBody("MOCK"));
    var mp = call(s, "createPayment", mock, 2L, false, payBody(1, "SM" + mock));
    call(s, "confirmPayment", lid(mp, "paymentId"), 2L, false, Map.of());
    assertEquals(
        "0.00",
        call(s, "summary", 1L, Map.of("enrollmentId", String.valueOf(mock))).get("receivedAmount"));
    assertEquals(
        "1000.00",
        call(s, "summary", 1L, Map.of("enrollmentId", String.valueOf(mock), "financeMode", "MOCK"))
            .get("todayReceivedAmount"));
  }

  @Test
  void replayStopsOnMissingSafetyIndexOrForeignKeyOrChangedGeneratedGuard() throws Exception {
    db.jdbc().execute("ALTER TABLE edu_tuition_payment DROP INDEX uk_tp_effective");
    try {
      assertThrows(AssertionError.class, this::migrate);
    } finally {
      db.jdbc()
          .execute(
              "ALTER TABLE edu_tuition_payment ADD UNIQUE INDEX"
                  + " uk_tp_effective(effective_enrollment_id)");
    }
    db.jdbc().execute("ALTER TABLE edu_tuition_payment DROP FOREIGN KEY fk_tp_recorder");
    try {
      assertThrows(AssertionError.class, this::migrate);
    } finally {
      db.jdbc()
          .execute(
              "ALTER TABLE edu_tuition_payment ADD CONSTRAINT fk_tp_recorder FOREIGN"
                  + " KEY(recorded_by) REFERENCES sys_user(user_id)");
    }
    db.jdbc()
        .execute(
            "ALTER TABLE edu_tuition_payment MODIFY effective_enrollment_id bigint GENERATED ALWAYS"
                + " AS(CASE WHEN payment_status='SUCCESS' THEN enrollment_id END) STORED");
    try {
      assertThrows(AssertionError.class, this::migrate);
    } finally {
      db.jdbc()
          .execute(
              "ALTER TABLE edu_tuition_payment MODIFY effective_enrollment_id bigint GENERATED"
                  + " ALWAYS AS(CASE WHEN payment_status IN ('PENDING','UNKNOWN','SUCCESS') THEN"
                  + " enrollment_id END) STORED");
    }
    migrate();
  }

  int race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var ready = new CountDownLatch(1);
    try {
      var a =
          pool.submit(
              () -> {
                ready.await();
                return first.call();
              });
      var b =
          pool.submit(
              () -> {
                ready.await();
                return second.call();
              });
      ready.countDown();
      return (a.get() ? 1 : 0) + (b.get() ? 1 : 0);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentFinalCouponCapacityAndRefundApplicationsHaveOneWinner() throws Exception {
    var c = service("CouponService");
    long template = lid(call(c, "saveTemplate", 1L, null, templateBody(1)), "templateId");
    int granted =
        race(
            () -> {
              try {
                call(
                    c,
                    "issue",
                    template,
                    1L,
                    Map.of(
                        "parentIds",
                        List.of(2),
                        "reason",
                        "最后一张",
                        "idempotencyKey",
                        "LAST-A" + template));
                return true;
              } catch (Exception e) {
                return false;
              }
            },
            () -> {
              try {
                call(
                    c,
                    "issue",
                    template,
                    4L,
                    Map.of(
                        "parentIds",
                        List.of(3),
                        "reason",
                        "最后一张",
                        "idempotencyKey",
                        "LAST-B" + template));
                return true;
              } catch (Exception e) {
                return false;
              }
            });
    assertEquals(1, granted);
    assertEquals(
        1,
        db.jdbc()
            .queryForObject(
                "SELECT issued_quantity FROM edu_coupon_template WHERE template_id=?",
                Integer.class,
                template));
    assertEquals(
        1,
        db.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM edu_user_coupon WHERE template_id=?",
                Integer.class,
                template));
    var s = service("TuitionService");
    long id = fresh();
    call(s, "price", id, 1L, priceBody("MOCK"));
    var p = call(s, "createPayment", id, 2L, false, payBody(1, "RACE-R" + id));
    long payment = lid(p, "paymentId");
    assertEquals(
        2,
        race(
            () -> {
              call(s, "confirmPayment", payment, 2L, false, Map.of());
              return true;
            },
            () -> {
              call(s, "confirmPayment", payment, 2L, false, Map.of());
              return true;
            }));
    assertEquals(
        1,
        db.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM edu_tuition_receipt WHERE payment_id=?",
                Integer.class,
                payment));
    int applied =
        race(
            () -> {
              try {
                call(
                    s,
                    "applyRefund",
                    2L,
                    false,
                    Map.of(
                        "paymentId",
                        payment,
                        "refundKind",
                        "PARTIAL",
                        "requestedAmount",
                        "600",
                        "reason",
                        "并发申请",
                        "idempotencyKey",
                        "RA" + payment));
                return true;
              } catch (Exception e) {
                return false;
              }
            },
            () -> {
              try {
                call(
                    s,
                    "applyRefund",
                    1L,
                    true,
                    Map.of(
                        "paymentId",
                        payment,
                        "refundKind",
                        "PARTIAL",
                        "requestedAmount",
                        "600",
                        "reason",
                        "并发申请",
                        "idempotencyKey",
                        "RB" + payment));
                return true;
              } catch (Exception e) {
                return false;
              }
            });
    assertEquals(1, applied);
    assertEquals("400.00", call(s, "bill", id, 2L, false).get("refundableAmount"));
  }
}

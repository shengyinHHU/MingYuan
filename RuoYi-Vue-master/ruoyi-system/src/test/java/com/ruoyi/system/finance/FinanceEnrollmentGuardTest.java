package com.ruoyi.system.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.EduEnrollment;
import org.junit.jupiter.api.Test;

class FinanceEnrollmentGuardTest {
  EduEnrollment current() {
    var e = new EduEnrollment();
    e.setEnrollmentId(1L);
    e.setScheduleId(2L);
    e.setParentId(3L);
    e.setStudentName("学生");
    e.setEnrollmentCode("EN01");
    e.setPayStatus("已支付");
    e.setEnrollmentStatus("报名成功");
    e.setDelFlag("0");
    return e;
  }

  @Test
  void oldEditCannotUndoPaymentOrRewriteBillIdentity() {
    var patch = new EduEnrollment();
    patch.setPayStatus("未支付");
    assertThrows(
        ServiceException.class, () -> FinanceEnrollmentGuard.checkUpdate(current(), patch));
    patch.setPayStatus("已支付");
    patch.setStudentName("另一个人");
    assertThrows(
        ServiceException.class, () -> FinanceEnrollmentGuard.checkUpdate(current(), patch));
    patch.setStudentName("学生");
    patch.setEnrollmentStatus("已取消");
    assertThrows(
        ServiceException.class, () -> FinanceEnrollmentGuard.checkUpdate(current(), patch));
  }

  @Test
  void contactAndRemarksMayChangeAndNumericLegacyStatesNormalize() {
    var patch = new EduEnrollment();
    patch.setContactPhone("13800000000");
    patch.setRemark("更新联系人");
    assertDoesNotThrow(() -> FinanceEnrollmentGuard.checkUpdate(current(), patch));
    var old = current();
    old.setPayStatus("1");
    old.setEnrollmentStatus("1");
    patch.setPayStatus("已支付");
    patch.setEnrollmentStatus("报名成功");
    assertDoesNotThrow(() -> FinanceEnrollmentGuard.checkUpdate(old, patch));
  }

  @Test
  void newEnrollmentFitsExistingCodeColumnAndCannotPretendPaid() {
    var e = new EduEnrollment();
    e.setScheduleId(2L);
    e.setParentId(3L);
    e.setStudentName(" 学生 ");
    FinanceEnrollmentGuard.prepareNew(e);
    assertTrue(e.getEnrollmentCode().length() <= 32);
    assertEquals("学生", e.getStudentName());
    assertEquals("未支付", e.getPayStatus());
    e.setPayStatus("已支付");
    assertThrows(ServiceException.class, () -> FinanceEnrollmentGuard.prepareNew(e));
    e.setPayStatus("未支付");
    e.setEnrollmentCode("x".repeat(33));
    assertThrows(ServiceException.class, () -> FinanceEnrollmentGuard.prepareNew(e));
  }
}

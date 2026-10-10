package com.ruoyi.system.finance;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.EduEnrollment;
import java.util.Objects;

/** Old CRUD can update contacts, but must not change finance facts or bill identity. */
public final class FinanceEnrollmentGuard {
  private FinanceEnrollmentGuard() {}

  public static void prepareNew(EduEnrollment e) {
    if (e.getScheduleId() == null
        || e.getParentId() == null
        || e.getStudentName() == null
        || e.getStudentName().isBlank()
        || e.getStudentName().trim().length() > 30)
      throw new ServiceException("请选择课程、家长并填写30字以内的学生姓名");
    if (e.getPayStatus() != null && !java.util.List.of("0", "未支付").contains(e.getPayStatus()))
      throw new ServiceException("新增报名不能直接标记已收款，请在收费财务中登记");
    String code = e.getEnrollmentCode();
    if (code == null || code.isBlank())
      code = "ENR" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    if (code.length() > 32) throw new ServiceException("报名编码最多32字");
    if (e.getStudentPhone() != null && e.getStudentPhone().length() > 11
        || e.getContactPhone() != null && e.getContactPhone().length() > 11)
      throw new ServiceException("联系电话最多11位");
    e.setEnrollmentCode(code);
    e.setStudentName(e.getStudentName().trim());
    e.setPayStatus("未支付");
    e.setEnrollmentStatus("报名成功");
    e.setDelFlag("0");
    e.setStatus("0");
    e.setCancelTime(null);
    e.setCancelReason(null);
  }

  public static void checkUpdate(EduEnrollment old, EduEnrollment patch) {
    if (old == null) throw new ServiceException("报名记录不存在");
    same(patch.getScheduleId(), old.getScheduleId());
    same(patch.getParentId(), old.getParentId());
    same(patch.getStudentName(), old.getStudentName());
    same(patch.getEnrollmentCode(), old.getEnrollmentCode());
    if (patch.getPayStatus() != null && !pay(patch.getPayStatus()).equals(pay(old.getPayStatus())))
      throw new ServiceException("请在收费财务中登记或核验收款，不能直接修改支付状态");
    if (patch.getEnrollmentStatus() != null
        && !enrollment(patch.getEnrollmentStatus()).equals(enrollment(old.getEnrollmentStatus())))
      throw new ServiceException("报名状态已纳入财务保护，请使用取消报名或退费流程");
    if (patch.getDelFlag() != null && !Objects.equals(patch.getDelFlag(), old.getDelFlag()))
      throw new ServiceException("请通过受保护的取消报名入口处理");
    // Do not allow generic CRUD to overwrite original operator/timestamps or cancellation history.
    patch.setCreateBy(null);
    patch.setCreateTime(null);
    patch.setCancelTime(null);
    patch.setCancelReason(null);
  }

  private static void same(Object proposed, Object current) {
    if (proposed != null && !Objects.equals(proposed, current))
      throw new ServiceException("报名身份和课程快照不能直接修改，请取消后重新报名");
  }

  private static String pay(String s) {
    return switch (Objects.toString(s, "")) {
      case "0" -> "未支付";
      case "1" -> "已支付";
      case "2" -> "已退款";
      default -> Objects.toString(s, "");
    };
  }

  private static String enrollment(String s) {
    return switch (Objects.toString(s, "")) {
      case "0" -> "待确认";
      case "1" -> "报名成功";
      case "2" -> "已取消";
      default -> Objects.toString(s, "");
    };
  }
}

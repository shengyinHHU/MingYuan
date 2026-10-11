package com.ruoyi.system.lesson;

import static com.ruoyi.system.finance.FinanceRules.*;
import com.ruoyi.system.shop.ShopRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Hooks join the caller's payment/refund transaction. No dependency on TuitionService. */
@Service
public class LessonFinance {
  private final ShopRepository db;
  public LessonFinance(ShopRepository db) {this.db=db;}
  private Map<String,Object> packageForBill(long enrollment,long actor) {
    require(TransactionSynchronizationManager.isActualTransactionActive(),"课包财务必须与收退款同一事务");
    require(db.rows("SELECT user_id FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0'",actor).size()==1,"操作账号无效");
    var bill=db.one("SELECT * FROM edu_enrollment WHERE enrollment_id=? FOR UPDATE",enrollment);
    if(!"LESSON_PACKAGE".equals(bill.get("billType")))return null;
    return db.one("SELECT * FROM edu_student_package WHERE enrollment_id=? FOR UPDATE",enrollment);
  }
  public void activate(long enrollmentId,long actor) {
    var p=packageForBill(enrollmentId,actor);if(p==null)return;
    require(!Set.of("CLOSED","REFUND_FROZEN").contains(p.get("status")),"课包关闭或冻结，不能启用");
    require(!db.rows("SELECT payment_id FROM edu_tuition_payment WHERE enrollment_id=? AND payment_status='SUCCESS' AND confirmed_time IS NOT NULL AND paid_time IS NOT NULL FOR UPDATE",enrollmentId).isEmpty(),"确认收款后才可启用课包");
    if("ACTIVE".equals(p.get("status")))return;
    require("PENDING_PAYMENT".equals(p.get("status")),"课包状态不可启用");
    db.update("edu_student_package","student_package_id",id(p.get("studentPackageId")),values("status","ACTIVE"));
    change(p,null,null,"ACTIVATE",integer(p.get("totalUnits"),1,Integer.MAX_VALUE),"ACTIVATE:"+p.get("studentPackageId"),actor,"确认收款启用");
    db.update("edu_student_package","student_package_id",id(p.get("studentPackageId")),values("status","ACTIVE","activated_time",now(),"update_by",String.valueOf(actor),"update_time",now()));
  }
  private Map<String,Object> refund(long enrollment,long refund) {
    return db.one("SELECT r.* FROM edu_tuition_refund r JOIN edu_tuition_payment p ON p.payment_id=r.payment_id WHERE r.refund_id=? AND p.enrollment_id=? FOR UPDATE",refund,enrollment);
  }
  public void freeze(long enrollmentId,long refundId,long actor) {
    var p=packageForBill(enrollmentId,actor);if(p==null)return;
    var r=refund(enrollmentId,refundId);
    require("WITHDRAWAL".equals(r.get("refundKind")),"课包仅支持终止剩余课包退款");
    require(Set.of("PENDING_REVIEW","APPROVED","PROCESSING","UNKNOWN").contains(r.get("refundStatus")),"退款状态不可冻结");
    long packageId=id(p.get("studentPackageId"));
    if("REFUND_FROZEN".equals(p.get("status"))) {
      require(frozenBy(packageId,refundId),"课包已被另一退款冻结");return;
    }
    require("ACTIVE".equals(p.get("status")),"仅已启用课包可申请退款");
    // Current locking read works even inside an older REPEATABLE_READ finance transaction.
    // All lesson mutations lock package before booking, so this cannot reverse cancellation locks.
    require(db.rows("SELECT booking_id FROM edu_one_to_one_booking WHERE student_package_id=? AND status='BOOKED' AND end_time_snapshot>? FOR UPDATE",packageId,now()).isEmpty(),"请先处理所有未结束预约再申请退款");
    db.jdbc().update("UPDATE edu_tuition_refund SET calculation_snapshot=JSON_SET(calculation_snapshot,'$.lessonStudentPackageId',?,'$.lessonRemainingUnits',?,'$.lessonFreezeApplied',true,'$.lessonFreezeReleased',false),update_time=? WHERE refund_id=?",String.valueOf(packageId),p.get("availableUnits"),now(),refundId);
    db.update("edu_student_package","student_package_id",packageId,values("status","REFUND_FROZEN","update_by",String.valueOf(actor),"update_time",now()));
  }
  private boolean frozenBy(long packageId,long refundId) {
    return !db.rows("SELECT refund_id FROM edu_tuition_refund WHERE refund_id=? AND JSON_UNQUOTE(JSON_EXTRACT(calculation_snapshot,'$.lessonStudentPackageId'))=? AND JSON_EXTRACT(calculation_snapshot,'$.lessonFreezeApplied')=true AND JSON_EXTRACT(calculation_snapshot,'$.lessonFreezeReleased')=false FOR UPDATE",refundId,String.valueOf(packageId)).isEmpty();
  }
  public void unfreeze(long enrollmentId,long actor) {
    var p=packageForBill(enrollmentId,actor);if(p==null||!"REFUND_FROZEN".equals(p.get("status")))return;
    long packageId=id(p.get("studentPackageId"));
    var refunds=db.rows("SELECT r.* FROM edu_tuition_refund r JOIN edu_tuition_payment t ON t.payment_id=r.payment_id WHERE t.enrollment_id=? FOR UPDATE",enrollmentId);
    require(refunds.stream().noneMatch(r->Set.of("PENDING_REVIEW","APPROVED","PROCESSING","UNKNOWN","SUCCESS").contains(r.get("refundStatus"))),"在途或成功退款不能解冻课包");
    var matching=refunds.stream().filter(r->frozenBy(packageId,id(r.get("refundId")))).toList();
    require(matching.size()==1&&Set.of("REJECTED","CANCELLED","FAILED").contains(matching.get(0).get("refundStatus")),"缺少可解冻的原退款记录");
    db.jdbc().update("UPDATE edu_tuition_refund SET calculation_snapshot=JSON_SET(calculation_snapshot,'$.lessonFreezeReleased',true),update_time=? WHERE refund_id=?",now(),matching.get(0).get("refundId"));
    db.update("edu_student_package","student_package_id",packageId,values("status","ACTIVE","update_by",String.valueOf(actor),"update_time",now()));
  }
  public void close(long enrollmentId,long refundId,long actor) {
    var p=packageForBill(enrollmentId,actor);if(p==null)return;
    var r=refund(enrollmentId,refundId);
    require("SUCCESS".equals(r.get("refundStatus")),"退款到账成功后才可关闭课包");
    long packageId=id(p.get("studentPackageId"));String key="REFUND_CLOSE:"+refundId;
    if("CLOSED".equals(p.get("status"))) {
      require(!db.rows("SELECT log_id FROM edu_lesson_unit_log WHERE student_package_id=? AND event_key=?",packageId,key).isEmpty(),"课包已因其他操作关闭");return;
    }
    require("REFUND_FROZEN".equals(p.get("status"))&&frozenBy(packageId,refundId),"课包未被当前退款冻结");
    change(p,null,refundId,"REFUND_CLOSE",-integer(p.get("availableUnits"),0,Integer.MAX_VALUE),key,actor,"退款成功终止剩余课包");
    db.update("edu_student_package","student_package_id",packageId,values("status","CLOSED","closed_time",now(),"update_by",String.valueOf(actor),"update_time",now()));
  }
  /** Package row must already be locked. Append event and balance in the same caller transaction. */
  void change(Map<String,Object> p,Long booking,Long refund,String event,int delta,String key,long actor,String reason) {
    int before=integer(p.get("availableUnits"),0,Integer.MAX_VALUE);long after=(long)before+delta;
    require(after>=0&&after<=integer(p.get("totalUnits"),1,Integer.MAX_VALUE),"课包次数不足或退次超过总次数");
    long packageId=id(p.get("studentPackageId"));
    db.update("edu_student_package","student_package_id",packageId,values("available_units",after,"update_by",String.valueOf(actor),"update_time",now()));
    db.insert("edu_lesson_unit_log",values("student_package_id",packageId,"booking_id",booking,"refund_id",refund,"event_type",event,"delta_units",delta,"before_units",before,"after_units",after,"event_key",key,"actor_id",actor,"reason",reason,"create_by",String.valueOf(actor),"update_by",String.valueOf(actor)));
    p.put("availableUnits",(int)after);
  }
}

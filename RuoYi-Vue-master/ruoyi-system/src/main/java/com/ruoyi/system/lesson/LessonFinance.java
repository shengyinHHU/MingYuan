package com.ruoyi.system.lesson;

import static com.ruoyi.system.finance.FinanceRules.*;
import com.alibaba.fastjson2.JSON;
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
    require(Set.of("WAIT_EDU","WAIT_ADMIN","PENDING_REVIEW","APPROVED","PROCESSING","UNKNOWN").contains(r.get("refundStatus")),"退款状态不可冻结");
    require(integer(p.getOrDefault("reservedRefundUnits",0),0,Integer.MAX_VALUE)==0,"请先处理单次退款预留");
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
    require(refunds.stream().noneMatch(r->"WITHDRAWAL".equals(r.get("refundKind"))&&Set.of("WAIT_EDU","WAIT_ADMIN","PENDING_REVIEW","APPROVED","PROCESSING","UNKNOWN","SUCCESS").contains(r.get("refundStatus"))),"在途或成功整包退款不能解冻课包");
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

  private static final String TARGET_USED = "refund_status IN ('WAIT_EDU','WAIT_ADMIN','PENDING_REVIEW','APPROVED','PROCESSING','UNKNOWN','SUCCESS')";

  public List<Map<String,Object>> refundTargets(Map<String,Object> e) {
    if (!"OPEN".equals(e.get("billingStatus")) || Set.of("2","已取消").contains(str(e,"enrollmentStatus"))) return List.of();
    if ("LESSON_PACKAGE".equals(e.get("billType")))
      return db.rows("SELECT 'LESSON_BOOKING' target_type,CAST(b.booking_id AS CHAR) target_id,CONCAT(DATE_FORMAT(b.start_time_snapshot,'%Y-%m-%d %H:%i:%s'),' · ',p.package_name_snapshot,'（',p.subject_name_snapshot,'） · ',COALESCE(NULLIF(u.nick_name,''),'教师未设置'),' · 已返',b.returned_units-b.cash_refunded_units,'次') label,b.returned_units-b.cash_refunded_units returned_units FROM edu_student_package p JOIN edu_one_to_one_booking b ON b.student_package_id=p.student_package_id LEFT JOIN sys_user u ON u.user_id=b.teacher_id WHERE p.enrollment_id=? AND p.status='ACTIVE' AND b.status='CANCELLED' AND b.returned_units>b.cash_refunded_units AND NOT EXISTS(SELECT 1 FROM edu_tuition_refund r WHERE r.lesson_booking_id=b.booking_id AND "+TARGET_USED+") ORDER BY b.start_time_snapshot,b.booking_id",e.get("enrollmentId"));
    return db.rows("SELECT 'CLASS_SIGN_IN' target_type,CAST(s.sign_in_id AS CHAR) target_id,CONCAT(s.class_date,' · ',c.course_class_name,' · ',COALESCE(NULLIF(c.teacher_name,''),NULLIF(u.nick_name,''),'教师未设置'),' · 已返0次') label,0 returned_units FROM edu_class_sign_in s JOIN edu_course_schedule c ON c.schedule_id=s.schedule_id LEFT JOIN sys_user u ON u.user_id=c.teacher_id WHERE s.enrollment_id=? AND s.schedule_id=? AND s.source_type='1' AND s.sign_status='3' AND s.review_status='1' AND s.del_flag='0' AND NOT EXISTS(SELECT 1 FROM edu_tuition_refund r WHERE r.class_sign_in_id=s.sign_in_id AND "+TARGET_USED+") ORDER BY s.class_date,s.sign_in_id",e.get("enrollmentId"),e.get("scheduleId"));
  }

  /** Finance holds bill/payment/refund first; never acquire student locks from these hooks. */
  public void validateSession(long enrollmentId,long refundId,long actor) {
    require(TransactionSynchronizationManager.isActualTransactionActive(),"课次核对必须在财务事务内");
    var r=refund(enrollmentId,refundId);
    if (!"SESSION".equals(r.get("refundKind"))) return;
    validateSessionTarget(enrollmentId,r.get("classSignInId")==null?null:id(r.get("classSignInId")),r.get("lessonBookingId")==null?null:id(r.get("lessonBookingId")),actor);
  }

  public void validateSessionTarget(long enrollmentId,Long signIn,Long booking,long actor) {
    require(TransactionSynchronizationManager.isActualTransactionActive(),"课次核对必须在财务事务内");
    var e=db.one("SELECT * FROM edu_enrollment WHERE enrollment_id=? FOR UPDATE",enrollmentId);
    if (signIn!=null) {
      require(!"LESSON_PACKAGE".equals(e.get("billType")),"课次不属于当前账单");
      var s=db.one("SELECT * FROM edu_class_sign_in WHERE sign_in_id=? FOR UPDATE",signIn);
      require(Objects.equals(s.get("enrollmentId"),e.get("enrollmentId")) && Objects.equals(s.get("scheduleId"),e.get("scheduleId"))
          && "1".equals(s.get("sourceType")) && "3".equals(s.get("signStatus")) && "1".equals(s.get("reviewStatus")) && "0".equals(s.get("delFlag")),"请假课次状态或归属已变化，请核对后处理");
    } else {
      var p=packageForBill(enrollmentId,actor);require(p!=null && "ACTIVE".equals(p.get("status")),"课包状态不可单次退款");
      var b=db.one("SELECT * FROM edu_one_to_one_booking WHERE booking_id=? AND student_package_id=? FOR UPDATE",booking,p.get("studentPackageId"));
      require("CANCELLED".equals(b.get("status")) && integer(b.get("returnedUnits"),0,2)>integer(b.get("cashRefundedUnits"),0,2),"预约未取消或已返次数不可现金退款");
    }
  }

  private Map<String,Object> sessionBooking(Map<String,Object> p,Map<String,Object> r) {
    return db.one("SELECT * FROM edu_one_to_one_booking WHERE booking_id=? AND student_package_id=? FOR UPDATE",r.get("lessonBookingId"),p.get("studentPackageId"));
  }

  public void reserveSession(long enrollmentId,long refundId,long actor) {
    validateSession(enrollmentId,refundId,actor);
    var r=refund(enrollmentId,refundId);if(r.get("lessonBookingId")==null)return;
    var p=packageForBill(enrollmentId,actor);var b=sessionBooking(p,r);
    int units=integer(b.get("returnedUnits"),0,2)-integer(b.get("cashRefundedUnits"),0,2),reserved=integer(p.get("reservedRefundUnits"),0,Integer.MAX_VALUE);
    require(integer(p.get("availableUnits"),0,Integer.MAX_VALUE)-reserved>=units,"已返次数已消费，无法预留现金退款");
    var audit=JSON.parseObject(str(r,"calculationSnapshot"));
    require(!audit.containsKey("lessonReservedUnits"),"当前退款已预留次数");
    audit.put("lessonReservedUnits",units);audit.put("lessonStudentPackageId",str(p,"studentPackageId"));
    db.update("edu_tuition_refund","refund_id",refundId,values("calculation_snapshot",JSON.toJSONString(audit)));
    db.update("edu_student_package","student_package_id",id(p.get("studentPackageId")),values("reserved_refund_units",reserved+units,"update_by",String.valueOf(actor),"update_time",now()));
  }

  public void releaseSession(long enrollmentId,long refundId,long actor) {
    var p=packageForBill(enrollmentId,actor);if(p==null)return;
    var r=refund(enrollmentId,refundId);require("REJECTED".equals(r.get("refundStatus")),"仅驳回可释放次数");
    var audit=JSON.parseObject(str(r,"calculationSnapshot"));
    if(Boolean.TRUE.equals(audit.getBoolean("lessonReserveReleased")))return;
    int units=integer(audit.get("lessonReservedUnits"),1,2),reserved=integer(p.get("reservedRefundUnits"),0,Integer.MAX_VALUE);
    require(reserved>=units,"退费预留次数不一致");
    audit.put("lessonReserveReleased",true);
    db.update("edu_tuition_refund","refund_id",refundId,values("calculation_snapshot",JSON.toJSONString(audit)));
    db.update("edu_student_package","student_package_id",id(p.get("studentPackageId")),values("reserved_refund_units",reserved-units,"update_by",String.valueOf(actor),"update_time",now()));
  }

  public void reclaimSession(long enrollmentId,long refundId,long actor) {
    var p=packageForBill(enrollmentId,actor);if(p==null)return;
    var r=refund(enrollmentId,refundId);require("SUCCESS".equals(r.get("refundStatus")),"现金退款成功后才可收回次数");
    String key="REFUND_RECLAIM:"+refundId;
    if(!db.rows("SELECT log_id FROM edu_lesson_unit_log WHERE event_key=?",key).isEmpty())return;
    validateSession(enrollmentId,refundId,actor);var b=sessionBooking(p,r);
    int units=integer(JSON.parseObject(str(r,"calculationSnapshot")).get("lessonReservedUnits"),1,2),reserved=integer(p.get("reservedRefundUnits"),0,Integer.MAX_VALUE);
    require(reserved>=units && integer(b.get("returnedUnits"),0,2)-integer(b.get("cashRefundedUnits"),0,2)==units,"退费课次与预留次数不一致");
    db.update("edu_student_package","student_package_id",id(p.get("studentPackageId")),values("reserved_refund_units",reserved-units));
    db.update("edu_one_to_one_booking","booking_id",id(b.get("bookingId")),values("cash_refunded_units",integer(b.get("cashRefundedUnits"),0,2)+units));
    change(p,id(b.get("bookingId")),refundId,"REFUND_RECLAIM",-units,key,actor,"单次现金退款收回已返次数");
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

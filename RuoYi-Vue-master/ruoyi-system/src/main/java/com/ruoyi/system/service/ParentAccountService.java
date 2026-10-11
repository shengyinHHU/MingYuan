package com.ruoyi.system.service;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.mapper.EduCourseScheduleMapper;
import com.ruoyi.system.shop.ShopRepository;

/** 家长个人交易视图。只聚合现有报名、课次及商城记录，不维护另一份余额或现金流水。 */
@Service
public class ParentAccountService {
    @Autowired private ShopRepository db;
    @Autowired private EduCourseScheduleMapper schedules;
    public static final String PENDING="e.del_flag='0' AND e.status='0' AND e.enrollment_status IN ('0','1','待确认','已确认','报名成功') AND e.settlement_method='C' AND e.pay_status IN ('0','未支付') AND NOT EXISTS(SELECT 1 FROM edu_tuition_payment p WHERE p.enrollment_id=e.enrollment_id AND p.payment_status IN ('PENDING','UNKNOWN','SUCCESS'))";
    private void access(Long parentId,boolean administrator) {
        if(administrator) LessonCreditService.admin();
        else if(!SecurityUtils.hasRole("parent") || !Objects.equals(parentId,SecurityUtils.getUserId())) throw new ServiceException("无权查看其他家长的个人账户");
    }
    public long pending(boolean administrator) {
        if(administrator) LessonCreditService.admin(); else access(SecurityUtils.getUserId(),false);
        return count("SELECT COUNT(*) FROM edu_enrollment e WHERE "+PENDING+(administrator?"":" AND e.parent_id=?"),administrator?new Object[]{}:new Object[]{SecurityUtils.getUserId()});
    }
    public Map<String,Object> list(String keyword,int page) {
        LessonCreditService.admin(); page(page); if(keyword == null) keyword="";
        if(keyword.length()>100) throw new ServiceException("搜索词过长"); String like="%"+keyword.trim()+"%";
        String from=" FROM sys_user u WHERE (EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=u.user_id AND r.role_key='parent') OR EXISTS(SELECT 1 FROM edu_enrollment e WHERE e.parent_id=u.user_id) OR EXISTS(SELECT 1 FROM edu_student_lesson_account a WHERE a.parent_id=u.user_id) OR EXISTS(SELECT 1 FROM edu_material_order o WHERE o.parent_id=u.user_id)) AND (u.user_name LIKE ? OR u.phonenumber LIKE ? OR EXISTS(SELECT 1 FROM edu_student_lesson_account a WHERE a.parent_id=u.user_id AND (a.student_name LIKE ? OR a.contact_phone LIKE ?)) OR EXISTS(SELECT 1 FROM edu_enrollment e WHERE e.parent_id=u.user_id AND (e.student_name LIKE ? OR e.contact_phone LIKE ?)))";
        String select="SELECT u.user_id AS parent_id,u.user_name AS parent_name,u.phonenumber AS parent_phone,u.status,u.del_flag,(SELECT COUNT(*) FROM edu_student_lesson_account a WHERE a.parent_id=u.user_id) AS child_count,(SELECT COUNT(*) FROM edu_enrollment e WHERE e.parent_id=u.user_id AND "+PENDING+") AS pending_count,(SELECT MAX(COALESCE(e.update_time,e.create_time)) FROM edu_enrollment e WHERE e.parent_id=u.user_id) AS modified_time";
        var rows=db.rows(select+from+" ORDER BY COALESCE(modified_time,'1970-01-01') DESC,u.user_id DESC LIMIT 30 OFFSET ?",like,like,like,like,like,like,(page-1)*30);
        return Map.of("rows",rows,"total",count("SELECT COUNT(*)"+from,like,like,like,like,like,like),"pendingCount",pending(true));
    }
    public Map<String,Object> detail(Long parentId,boolean administrator) {
        access(parentId,administrator);
        var result=new LinkedHashMap<String,Object>();
        result.put("parent",db.one("SELECT user_id AS parent_id,user_name AS parent_name,phonenumber AS parent_phone FROM sys_user WHERE user_id=?",parentId));
        result.put("children",db.rows("SELECT a.*,a.remaining_credits-a.reserved_credits AS available_credits FROM edu_student_lesson_account a WHERE a.parent_id=? ORDER BY a.account_id",parentId).stream().map(a->{a.remove("requestKey");return a;}).toList());
        result.put("pendingCount",count("SELECT COUNT(*) FROM edu_enrollment e WHERE e.parent_id=? AND "+PENDING,parentId));
        result.put("unlinkedCount",count("SELECT COUNT(*) FROM edu_enrollment WHERE parent_id=? AND lesson_account_id IS NULL",parentId));
        return result;
    }
    /** 三类记录单独分页，避免只显示前若干孩子或付款导致历史缺失。 */
    public Map<String,Object> records(Long parentId,boolean administrator,String kind,int page,boolean pendingOnly) {
        access(parentId,administrator); page(page);
        String query;
        if("enrollments".equals(kind)) {
            query="SELECT CONCAT('E:',e.enrollment_id) AS record_key,e.enrollment_id,e.enrollment_code,e.lesson_account_id,e.student_name,a.student_name AS child_name,e.contact_phone,e.enrollment_status,e.pay_status,e.settlement_method,e.billing_status,e.payable_amount,(SELECT COALESCE(SUM(p.amount),0) FROM edu_tuition_payment p WHERE p.enrollment_id=e.enrollment_id AND p.payment_status='SUCCESS') AS received_amount,(SELECT COALESCE(SUM(r.approved_amount),0) FROM edu_tuition_refund r JOIN edu_tuition_payment p ON p.payment_id=r.payment_id WHERE p.enrollment_id=e.enrollment_id AND r.refund_status='SUCCESS') AS refunded_amount,e.class_date,e.create_time,COALESCE(e.update_time,e.create_time) AS modified_time,s.course_class_name,("+PENDING+") AS pending FROM edu_enrollment e LEFT JOIN edu_course_schedule s ON s.schedule_id=e.schedule_id LEFT JOIN edu_student_lesson_account a ON a.account_id=e.lesson_account_id WHERE e.parent_id=?"+(pendingOnly?" AND "+PENDING:"");
        } else if("orders".equals(kind)) {
            query="SELECT CONCAT('O:',o.order_id) AS record_key,o.order_id,o.order_code,o.order_status,o.pay_status,o.aftersale_status,o.payable_amount,CASE WHEN o.pay_status IN ('1','2') THEN o.amount ELSE NULL END AS paid_amount,o.refunded_amount,o.pay_time,o.refund_time,o.create_time,COALESCE(o.update_time,o.create_time) AS modified_time,(SELECT GROUP_CONCAT(i.material_title ORDER BY i.item_id SEPARATOR '、') FROM edu_material_order_item i WHERE i.order_id=o.order_id) AS title FROM edu_material_order o WHERE o.parent_id=?";
        } else if("history".equals(kind)) {
            // 操作记录为原始事件，额度与现金明确分开；各业务事件ID组成稳定排序键。
            query="SELECT CONCAT('C:',c.event_id) AS record_key,'CREDIT' AS source,c.action,c.quantity,c.reason,c.actor_name,c.create_time,c.create_time AS modified_time,a.student_name AS title,c.account_id AS reference_id,NULL AS previous_status,NULL AS next_status FROM edu_lesson_credit_event c JOIN edu_student_lesson_account a ON a.account_id=c.account_id WHERE a.parent_id=?"
                +" UNION ALL SELECT CONCAT('E:',v.event_id),'ENROLLMENT',v.action,NULL,v.reason,v.actor_name,v.create_time,v.create_time,CONCAT(e.student_name,' · ',COALESCE(s.course_class_name,'课程报名')),e.enrollment_id,v.previous_status,v.next_status FROM edu_enrollment_event v JOIN edu_enrollment e ON e.enrollment_id=v.enrollment_id LEFT JOIN edu_course_schedule s ON s.schedule_id=e.schedule_id WHERE e.parent_id=?"
                +" UNION ALL SELECT CONCAT('O:',v.log_id),'SHOP',v.event_type,NULL,v.remark,CAST(v.operator_id AS CHAR),v.create_time,v.create_time,o.order_code,o.order_id,v.from_pay_status,v.to_pay_status FROM edu_material_order_log v JOIN edu_material_order o ON o.order_id=v.order_id WHERE o.parent_id=?";
            query += " UNION ALL SELECT CONCAT('P:',p.payment_id),'TUITION',CONCAT('PAYMENT_',p.payment_status),NULL,p.failure_reason,u.user_name,p.create_time,COALESCE(p.update_time,p.create_time),CONCAT(e.student_name,' · ',COALESCE(e.fee_title_snapshot,'课程收款')),p.enrollment_id,NULL,p.payment_status FROM edu_tuition_payment p JOIN edu_enrollment e ON e.enrollment_id=p.enrollment_id LEFT JOIN sys_user u ON u.user_id=p.recorded_by WHERE e.parent_id=?"
                +" UNION ALL SELECT CONCAT('R:',r.refund_id),'TUITION',CONCAT('REFUND_',r.refund_status),NULL,r.reason,u.user_name,r.create_time,COALESCE(r.update_time,r.create_time),CONCAT(e.student_name,' · ',COALESCE(e.fee_title_snapshot,'课程退款')),e.enrollment_id,NULL,r.refund_status FROM edu_tuition_refund r JOIN edu_tuition_payment p ON p.payment_id=r.payment_id JOIN edu_enrollment e ON e.enrollment_id=p.enrollment_id LEFT JOIN sys_user u ON u.user_id=r.applicant_id WHERE e.parent_id=?";
        } else throw new ServiceException("记录类型无效");
        Object[] owners="history".equals(kind)?new Object[]{parentId,parentId,parentId,parentId,parentId}:new Object[]{parentId};
        long total=count("SELECT COUNT(*) FROM ("+query+") account_records",owners);
        var args=new ArrayList<>(Arrays.asList(owners)); args.add((page-1)*30);
        var rows=db.rows("SELECT * FROM ("+query+") account_records ORDER BY COALESCE(modified_time,'1970-01-01') DESC,record_key DESC LIMIT 30 OFFSET ?",args.toArray());
        for(var row:rows) if(row.get("classDate") instanceof java.sql.Date d) row.put("classDate",d.toLocalDate().toString());
        return Map.of("rows",rows,"total",total);
    }
    public static class Link { public Long accountId; public String reason; }
    @Transactional(rollbackFor=Exception.class)
    public void link(Long enrollmentId,Link body) {
        LessonCreditService.admin(); if(body == null || body.accountId == null || body.reason == null || body.reason.isBlank() || body.reason.length()>255) throw new ServiceException("请选择孩子并填写核对说明");
        var before=db.one("SELECT schedule_id FROM edu_enrollment WHERE enrollment_id=?",enrollmentId);
        schedules.lockSchedule(Long.valueOf(before.get("scheduleId").toString()));
        var e=db.one("SELECT * FROM edu_enrollment WHERE enrollment_id=? FOR UPDATE",enrollmentId);
        if(e.get("lessonAccountId")!=null) {
            if(body.accountId.toString().equals(e.get("lessonAccountId").toString())) return;
            throw new ServiceException("报名已关联孩子，不能直接改写课包与上课历史归属");
        }
        if("P".equals(e.get("settlementMethod"))) throw new ServiceException("课包历史缺少账户关联，须核对原暂占流水，不能按现金旧报名直接关联");
        db.one("SELECT account_id FROM edu_student_lesson_account WHERE account_id=? AND parent_id=? FOR UPDATE",body.accountId,e.get("parentId"));
        var child=db.one("SELECT student_id FROM edu_student_lesson_account WHERE account_id=?",body.accountId);
        if(e.get("studentId")!=null && !e.get("studentId").toString().equals(child.get("studentId").toString()))
            throw new ServiceException("报名已有孩子身份，不能改写归属");
        var now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        db.update("edu_enrollment","enrollment_id",enrollmentId,Map.of("lesson_account_id",body.accountId,"student_id",db.one("SELECT student_id FROM edu_student_lesson_account WHERE account_id=?",body.accountId).get("studentId"),"update_by",SecurityUtils.getUsername(),"update_time",now));
        var event=new LinkedHashMap<String,Object>(); event.put("enrollment_id",enrollmentId); event.put("action","LINK_CHILD"); event.put("previous_status",null); event.put("next_status",null); event.put("actor_id",SecurityUtils.getUserId()); event.put("actor_name",SecurityUtils.getUsername()); event.put("reason","关联孩子账户 "+body.accountId+"："+body.reason.trim()); event.put("create_time",now); db.insert("edu_enrollment_event",event);
    }
    private long count(String sql,Object...args) { return Objects.requireNonNull(db.jdbc().queryForObject(sql,Long.class,args)); }
    private static void page(int page) { if(page<1 || page>100000) throw new ServiceException("页码无效"); }
}

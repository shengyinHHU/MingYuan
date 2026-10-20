package com.ruoyi.system.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.model.MiniAppSignReviewBody;
import com.ruoyi.common.core.domain.model.MiniAppSignSubmitBody;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.SecurityUtils;
import java.util.Objects;
import com.ruoyi.system.domain.EduClassSignIn;
import com.ruoyi.system.domain.EduCourseSchedule;
import com.ruoyi.system.mapper.EduClassSignInMapper;
import com.ruoyi.system.mapper.EduCourseScheduleMapper;
import com.ruoyi.system.service.IEduClassSignInService;

/**
 * 课次签到 服务实现
 *
 * @author ruoyi
 * @date 2026-08-30
 */
@Service
public class EduClassSignInServiceImpl implements IEduClassSignInService
{
    /** 计入实际到课人数的签到结果：到课/试听到课/调课到课 */
    private static final Set<String> ATTENDED_STATUSES = new LinkedHashSet<>(Arrays.asList("1", "4", "5"));

    private static final Set<String> VALID_SIGN_STATUSES = new LinkedHashSet<>(Arrays.asList("1", "2", "3", "4", "5", "6"));

    private static final Set<String> VALID_SOURCE_TYPES = new LinkedHashSet<>(Arrays.asList("1", "2", "3"));

    @Autowired
    private EduClassSignInMapper signInMapper;

    @Autowired
    private EduCourseScheduleMapper scheduleMapper;
    @Autowired private com.ruoyi.system.shop.ShopRepository singleDb;
    @Autowired private com.ruoyi.system.service.ClassLeaveService classLeaves;

    @Override
    public List<EduClassSignIn> selectTeacherSchedules(Long teacherId)
    {
        return signInMapper.selectScheduleListForTeacher(teacherId);
    }

    @Override
    public List<Map<String, Object>> selectEnrolledStudents(Long scheduleId)
    {
        return signInMapper.selectEnrolledStudents(scheduleId);
    }

    @Override
    public Map<String, Object> selectDetail(Long scheduleId, String classDate)
    {
        Map<String, Object> info = signInMapper.selectReviewInfo(scheduleId, classDate);
        List<EduClassSignIn> details = signInMapper.selectDetailList(scheduleId, classDate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scheduleId", scheduleId);
        result.put("classDate", classDate);
        result.put("details", details);
        result.put("review", formatReviewInfo(info));
        result.put("stats", attendanceStats(details));
        result.put("submissionVersion", submissionVersion(details));
        if (!details.isEmpty()) {
            var first = details.get(0);
            result.put("teacherName", first.getTeacherName());
            result.put("timeSlot", first.getTimeSlot());
            result.put("submittedBy", first.getCreateBy());
            result.put("submittedTime", formatTime(first.getCreateTime()));
        }
        boolean reviewed = info != null && (Set.of("1", "2").contains(String.valueOf(info.get("review_status")))
            || info.get("review1_admin_id") != null || info.get("review2_admin_id") != null);
        result.put("approvedLeaveEnrollmentIds", reviewed ? java.util.Set.of() : classLeaves.approved(scheduleId,classDate));
        return result;
    }

    /**
     * 复核信息：统一转驼峰键，时间格式化为字符串，便于小程序展示
     */
    private Map<String, Object> formatReviewInfo(Map<String, Object> info)
    {
        Map<String, Object> review = new LinkedHashMap<>();
        if (info == null)
        {
            review.put("reviewStatus", "0");
            review.put("confirmedCount", 0);
            return review;
        }
        review.put("reviewStatus", str(info.get("review_status")));
        review.put("reviewReason", str(info.get("review_reason")));
        review.put("review1AdminId", info.get("review1_admin_id"));
        review.put("review1AdminName", info.get("review1_admin_name"));
        review.put("review1Images", str(info.get("review1_images")));
        review.put("review1Time", formatTime(info.get("review1_time")));
        review.put("review2AdminId", info.get("review2_admin_id"));
        review.put("review2AdminName", info.get("review2_admin_name"));
        review.put("review2Images", str(info.get("review2_images")));
        review.put("review2Time", formatTime(info.get("review2_time")));
        review.put("totalCount", info.get("total_count"));
        review.put("actualCount", info.get("actual_count"));
        int confirmed = 0;
        if (info.get("review1_admin_id") != null)
        {
            confirmed++;
        }
        if (info.get("review2_admin_id") != null)
        {
            confirmed++;
        }
        review.put("confirmedCount", confirmed);
        return review;
    }

    private String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private String formatTime(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Date)
        {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm").format((Date) value);
        }
        return String.valueOf(value);
    }

    @Override
    @Transactional
    public Map<String, Object> submitSignIn(MiniAppSignSubmitBody body, String operator)
    {
        Long scheduleId = body.getScheduleId();
        String classDate = body.getClassDate();
        List<MiniAppSignSubmitBody.Detail> details = body.getDetails();
        if (scheduleId == null || StringUtils.isEmpty(classDate) || details == null || details.isEmpty())
        {
            throw new ServiceException("缺少排课、课次日期或签到明细");
        }
        EduCourseSchedule schedule = scheduleMapper.lockSchedule(scheduleId);
        if (schedule == null || "2".equals(schedule.getDelFlag()))
        {
            throw new ServiceException("排课不存在或已删除");
        }
        if ("2".equals(schedule.getClassMode()) && body.getDetails() != null) {
            if(!singleDb.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND class_date=? AND lesson_completed_time IS NOT NULL FOR UPDATE",scheduleId,classDate).isEmpty())
                throw new ServiceException("本次一对一已确认完成，不能覆盖原签到或改变消课历史，请联系管理员核对");
            for (var detail : body.getDetails()) {
                if (detail.getEnrollmentId() == null) throw new ServiceException("一对一签到需关联已确认的单次报名");
                var rows = singleDb.rows("SELECT enrollment_id FROM edu_enrollment WHERE enrollment_id=? AND schedule_id=? AND class_date=? AND enrollment_status='1' AND del_flag='0' FOR UPDATE",detail.getEnrollmentId(),body.getScheduleId(),body.getClassDate());
                if (rows.isEmpty()) throw new ServiceException("单次报名日期或确认状态已变化，请刷新");
            }
        }
        if (!Objects.equals(schedule.getTeacherId(), SecurityUtils.getUserId()))
        {
            throw new ServiceException("排课教师已变更，无权提交该课程签到");
        }
        if (!classDate.matches("\\d{4}-\\d{2}-\\d{2}"))
        {
            throw new ServiceException("课次日期格式应为 yyyy-MM-dd");
        }

        // 已复核完成的课次锁定，不允许修改签到
        Map<String, Object> info = signInMapper.selectReviewInfo(scheduleId, classDate);
        if (info != null && (Set.of("1", "2").contains(String.valueOf(info.get("review_status")))
            || info.get("review1_admin_id") != null || info.get("review2_admin_id") != null))
        {
            throw new ServiceException("该课次已进入教务审核，异常记录请由教务或管理员更正，教师不能覆盖签到");
        }

        // 报名学员按稳定报名编号去重，同名孩子不能互相覆盖；自由添加的学员仍按姓名检查。
        List<EduClassSignIn> rows = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        Set<Long> enrollmentIds = new LinkedHashSet<>();
        Set<String> freeNames = new LinkedHashSet<>();
        for (MiniAppSignSubmitBody.Detail d : details)
        {
            if (d == null || StringUtils.isEmpty(d.getStudentName()))
            {
                throw new ServiceException("签到明细缺少学生姓名");
            }
            String status = d.getSignStatus();
            String source = d.getSourceType();
            if (!VALID_SIGN_STATUSES.contains(status))
            {
                throw new ServiceException("学生「" + d.getStudentName() + "」的签到结果无效");
            }
            if (!VALID_SOURCE_TYPES.contains(source))
            {
                throw new ServiceException("学生「" + d.getStudentName() + "」的学员来源无效");
            }
            String name = d.getStudentName().trim();
            boolean duplicate = d.getEnrollmentId() == null
                ? !freeNames.add(name) || names.contains(name)
                : !enrollmentIds.add(d.getEnrollmentId()) || freeNames.contains(name);
            if (duplicate)
            {
                throw new ServiceException("学生「" + d.getStudentName() + "」重复出现");
            }
            names.add(name);
            EduClassSignIn row = new EduClassSignIn();
            row.setScheduleId(scheduleId);
            row.setClassDate(java.sql.Date.valueOf(classDate));
            row.setEnrollmentId(d.getEnrollmentId());
            row.setStudentName(d.getStudentName().trim());
            row.setSourceType(source);
            row.setSignStatus(status);
            row.setCreateBy(operator);
            row.setRemark(d.getRemark());
            rows.add(row);
        }

        // 覆盖式提交：删除旧明细后插入新明细
        signInMapper.deleteByScheduleAndDate(scheduleId, classDate);
        signInMapper.batchInsert(rows);

        long actual = rows.stream().filter(r -> ATTENDED_STATUSES.contains(r.getSignStatus())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", rows.size());
        result.put("actualCount", actual);
        result.put("attended", rows.stream().filter(r -> "1".equals(r.getSignStatus())).count());
        result.put("recorded", rows.stream().filter(r -> "2".equals(r.getSignStatus())).count());
        result.put("leave", rows.stream().filter(r -> "3".equals(r.getSignStatus())).count());
        result.put("audited", rows.stream().filter(r -> "4".equals(r.getSignStatus())).count());
        result.put("transferred", rows.stream().filter(r -> "5".equals(r.getSignStatus())).count());
        return result;
    }

    @Override
    public List<EduClassSignIn> selectReviewList(String classDate)
    {
        return signInMapper.selectReviewList(classDate);
    }

    private Map<String, Object> attendanceStats(List<EduClassSignIn> rows)
    {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCount", rows.size());
        stats.put("actualCount", rows.stream().filter(r -> ATTENDED_STATUSES.contains(r.getSignStatus())).count());
        stats.put("attended", rows.stream().filter(r -> "1".equals(r.getSignStatus())).count());
        stats.put("recorded", rows.stream().filter(r -> "2".equals(r.getSignStatus())).count());
        stats.put("leave", rows.stream().filter(r -> "3".equals(r.getSignStatus())).count());
        stats.put("audited", rows.stream().filter(r -> "4".equals(r.getSignStatus())).count());
        stats.put("transferred", rows.stream().filter(r -> "5".equals(r.getSignStatus())).count());
        stats.put("absent", rows.stream().filter(r -> "6".equals(r.getSignStatus())).count());
        return stats;
    }

    /** Replacing any submitted row invalidates the sheet being reviewed; no duplicate snapshot table. */
    private String submissionVersion(List<EduClassSignIn> rows)
    {
        var sheet = rows.stream().sorted(java.util.Comparator.comparing(EduClassSignIn::getSignInId))
            .map(r -> Arrays.asList(r.getSignInId(), r.getEnrollmentId(), r.getStudentName(),
                r.getSourceType(), r.getSignStatus(), r.getRemark(), r.getCreateBy(),
                r.getTeacherName(), r.getTimeSlot(), r.getCourseClassName(),
                r.getCampusName(), r.getClassroomName(), r.getReviewStatus(),
                r.getReviewReason(), r.getReviewHistory())).toList();
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(com.alibaba.fastjson2.JSON.toJSONString(sheet).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> review(MiniAppSignReviewBody body, Long adminId, String adminName)
    {
        if (body == null || body.getScheduleId() == null || StringUtils.isEmpty(body.getClassDate()))
            throw new ServiceException("缺少排课或课次日期");
        Long scheduleId = body.getScheduleId();
        String classDate = body.getClassDate();
        try {
            if (!java.time.LocalDate.parse(classDate).toString().equals(classDate))
                throw new java.time.DateTimeException("Invalid date");
        } catch (java.time.DateTimeException e) {
            throw new ServiceException("课次日期格式应为 yyyy-MM-dd");
        }
        var schedule = scheduleMapper.lockSchedule(scheduleId);
        if (schedule == null || !"0".equals(schedule.getDelFlag()))
            throw new ServiceException("排课不存在或已删除");
        var details = signInMapper.selectDetailList(scheduleId, classDate);
        if (details.isEmpty()) throw new ServiceException("该课次尚未提交点名，不能审核");
        var info = signInMapper.selectReviewInfo(scheduleId, classDate);
        if (info != null && "1".equals(String.valueOf(info.get("review_status"))))
            return reviewResult(info); // Never overwrite the original auditor, time or images.
        if (!submissionVersion(details).equals(body.getSubmissionVersion()))
            throw new ServiceException("点名表已变化或版本缺失，请刷新核对后再确认");
        if ("2".equals(str(info.get("review_status"))) && !hasCorrection(details))
            throw new ServiceException("请先更正异常学员出勤，再重新审核确认");
        var images = body.getImages() == null ? List.<String>of() : body.getImages();
        if (images.size() > 9 || images.stream().anyMatch(Objects::isNull))
            throw new ServiceException("课堂图片最多9张");
        String imageList = String.join(",", images);
        if (imageList.length() > 2000) throw new ServiceException("课堂图片信息过长");
        int updated = info != null && info.get("review1_admin_id") != null
            ? signInMapper.completeLegacyReview(scheduleId, classDate, adminId, adminName, imageList)
            : signInMapper.updateReview1(scheduleId, classDate, adminId, adminName, imageList);
        if (updated != details.size()) throw new ServiceException("点名状态已变化，请刷新后再审核");
        return reviewResult(signInMapper.selectReviewInfo(scheduleId, classDate));
    }

    private boolean hasCorrection(List<EduClassSignIn> rows)
    {
        return rows.stream().anyMatch(r -> {
            var history = com.alibaba.fastjson2.JSON.parseArray(r.getReviewHistory());
            return history != null && !history.isEmpty()
                && "CORRECT".equals(history.getJSONObject(history.size() - 1).getString("action"));
        });
    }

    /** Use the existing sheet and schedule lock; never recreate identities or touch money/credits. */
    private List<EduClassSignIn> exceptionSheet(MiniAppSignReviewBody body)
    {
        if (body == null || body.getScheduleId() == null || body.getClassDate() == null)
            throw new ServiceException("请选择课次");
        try {
            if (!java.time.LocalDate.parse(body.getClassDate()).toString().equals(body.getClassDate()))
                throw new java.time.DateTimeException("Invalid date");
        } catch (java.time.DateTimeException e) { throw new ServiceException("课次日期格式应为 yyyy-MM-dd"); }
        var schedule = scheduleMapper.lockSchedule(body.getScheduleId());
        if (schedule == null || !"0".equals(schedule.getDelFlag())) throw new ServiceException("排课不存在或已删除");
        var rows = signInMapper.selectDetailList(body.getScheduleId(), body.getClassDate());
        if (rows.isEmpty()) throw new ServiceException("该课次尚未提交点名");
        if (rows.stream().anyMatch(r -> "1".equals(r.getReviewStatus())))
            throw new ServiceException("已确认课次已锁定，不可直接更正");
        if (!submissionVersion(rows).equals(body.getSubmissionVersion()))
            throw new ServiceException("点名表已变化，请刷新后操作");
        if (!singleDb.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND class_date=? AND lesson_completed_time IS NOT NULL FOR UPDATE",
                body.getScheduleId(), body.getClassDate()).isEmpty())
            throw new ServiceException("本课次已完成消课，不可直接更正，请先核对消课历史");
        if (!singleDb.rows("SELECT record_id FROM edu_teacher_salary_record WHERE teacher_id=? AND salary_month=? AND confirm_status='1' AND del_flag='0' FOR UPDATE",
                schedule.getTeacherId(), body.getClassDate().substring(0,7)).isEmpty())
            throw new ServiceException("该教师本月薪资已结算，不可直接更正出勤");
        return rows;
    }

    private String correctionReason(String value)
    {
        String reason = value == null ? "" : value.trim();
        if (reason.isEmpty() || reason.length() > 500) throw new ServiceException("请填写1至500字的异常或更正原因");
        return reason;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reject(MiniAppSignReviewBody body, Long adminId, String adminName)
    {
        var rows = exceptionSheet(body);
        body.setReason(correctionReason(body.getReason()));
        int updated = singleDb.jdbc().update("UPDATE edu_class_sign_in SET review_history=JSON_ARRAY_APPEND(COALESCE(review_history,JSON_ARRAY()),'$',"
            + "JSON_OBJECT('action','REJECT','beforeStatus',sign_status,'afterStatus',sign_status,'actorId',CAST(? AS CHAR),'actorName',?,'time',DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s'),'reason',?)),"
            + "review_reason=?,review_status='2',update_by=?,update_time=NOW() WHERE schedule_id=? AND class_date=? AND del_flag='0' AND review_status IN ('0','2')",
            adminId,adminName,body.getReason(),body.getReason(),adminName,body.getScheduleId(),body.getClassDate());
        if (updated != rows.size()) throw new ServiceException("课次已变化，请刷新后操作");
        return selectDetail(body.getScheduleId(), body.getClassDate());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> correct(MiniAppSignReviewBody body, Long adminId, String adminName)
    {
        var rows = exceptionSheet(body);
        if (rows.stream().anyMatch(r -> !"2".equals(r.getReviewStatus()))) throw new ServiceException("请先标记审核异常，再更正出勤");
        if (body.getCorrections() != null && (body.getSignInId() != null || body.getSignStatus() != null))
            throw new ServiceException("不能混用单人和批量更正参数");
        var changes = body.getCorrections() == null
            ? List.of(new MiniAppSignReviewBody.Correction(body.getSignInId(), body.getSignStatus(), body.getReason())) : body.getCorrections();
        if (changes.isEmpty() || changes.size() > rows.size()) throw new ServiceException("请选择本课次需要更正的学员");
        var identities = new java.util.HashMap<Long, EduClassSignIn>();
        rows.forEach(row -> identities.put(row.getSignInId(), row));
        var seen = new java.util.HashSet<Long>();
        // Validate the entire batch before writing; the transaction also rolls back any SQL failure.
        for (var change : changes) {
            var row = change == null ? null : identities.get(change.getSignInId());
            if (row == null) throw new ServiceException("该学员不属于当前课次");
            if (!seen.add(row.getSignInId())) throw new ServiceException("同一学员不能重复更正");
            var allowed = "2".equals(row.getSourceType()) ? Set.of("3","4","6")
                : "3".equals(row.getSourceType()) ? Set.of("3","5","6") : Set.of("1","2","3","6");
            if (change.getSignStatus() == null || !allowed.contains(change.getSignStatus()))
                throw new ServiceException("请选择符合学员来源的出勤状态");
            if (change.getSignStatus().equals(row.getSignStatus())) throw new ServiceException("学生「" + row.getStudentName() + "」的出勤状态未改变");
            change.setReason(correctionReason(change.getReason()));
        }
        for (var change : changes) {
            int updated = singleDb.jdbc().update("UPDATE edu_class_sign_in SET review_history=JSON_ARRAY_APPEND(COALESCE(review_history,JSON_ARRAY()),'$',"
                + "JSON_OBJECT('action','CORRECT','beforeStatus',sign_status,'afterStatus',?,'actorId',CAST(? AS CHAR),'actorName',?,'time',DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s'),'reason',?)),"
                + "sign_status=?,update_by=?,update_time=NOW() WHERE sign_in_id=? AND schedule_id=? AND class_date=? AND del_flag='0' AND review_status='2'",
                change.getSignStatus(),adminId,adminName,change.getReason(),change.getSignStatus(),adminName,change.getSignInId(),body.getScheduleId(),body.getClassDate());
            if (updated != 1) throw new ServiceException("出勤已变化，请刷新后操作");
        }
        return selectDetail(body.getScheduleId(), body.getClassDate());
    }

    private Map<String, Object> reviewResult(Map<String, Object> info)
    {
        Map<String, Object> result = formatReviewInfo(info);
        result.put("requiredConfirmations", 1);
        return result;
    }
}

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

    private static final Set<String> VALID_SIGN_STATUSES = new LinkedHashSet<>(Arrays.asList("1", "2", "3", "4", "5"));

    private static final Set<String> VALID_SOURCE_TYPES = new LinkedHashSet<>(Arrays.asList("1", "2", "3"));

    @Autowired
    private EduClassSignInMapper signInMapper;

    @Autowired
    private EduCourseScheduleMapper scheduleMapper;

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
        EduCourseSchedule schedule = scheduleMapper.selectEduCourseScheduleByScheduleId(scheduleId);
        if (schedule == null || "2".equals(schedule.getDelFlag()))
        {
            throw new ServiceException("排课不存在或已删除");
        }
        if (!classDate.matches("\\d{4}-\\d{2}-\\d{2}"))
        {
            throw new ServiceException("课次日期格式应为 yyyy-MM-dd");
        }

        // 已复核完成的课次锁定，不允许修改签到
        Map<String, Object> info = signInMapper.selectReviewInfo(scheduleId, classDate);
        if (info != null && "1".equals(String.valueOf(info.get("review_status"))))
        {
            throw new ServiceException("该课次已复核完成，不能修改签到");
        }

        // 校验并按学生姓名去重（同一课次同一学生仅一条）
        List<EduClassSignIn> rows = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
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
            if (!names.add(d.getStudentName().trim()))
            {
                throw new ServiceException("学生「" + d.getStudentName() + "」重复出现");
            }
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

    @Override
    @Transactional
    public Map<String, Object> review(MiniAppSignReviewBody body, Long adminId, String adminName)
    {
        Long scheduleId = body.getScheduleId();
        String classDate = body.getClassDate();
        if (scheduleId == null || StringUtils.isEmpty(classDate))
        {
            throw new ServiceException("缺少排课或课次日期");
        }
        int rows = signInMapper.countByScheduleAndDate(scheduleId, classDate);
        if (rows == 0)
        {
            throw new ServiceException("该课次尚未签到，无法复核");
        }
        String images = body.getImages() == null ? "" : String.join(",", body.getImages());

        Map<String, Object> info = signInMapper.selectReviewInfo(scheduleId, classDate);
        Object r1 = info == null ? null : info.get("review1_admin_id");
        Object r2 = info == null ? null : info.get("review2_admin_id");
        Long review1Admin = r1 == null ? null : Long.valueOf(String.valueOf(r1));
        Long review2Admin = r2 == null ? null : Long.valueOf(String.valueOf(r2));

        if (review1Admin == null)
        {
            // 我是第1位确认管理员
            signInMapper.updateReview1(scheduleId, classDate, adminId, adminName, images);
        }
        else if (review1Admin.equals(adminId))
        {
            // 本人已确认过，仅更新图片
            signInMapper.updateReview1Images(scheduleId, classDate, adminId, adminName, images);
        }
        else if (review2Admin == null)
        {
            // 我是第2位确认管理员，复核完成
            int updated = signInMapper.updateReview2(scheduleId, classDate, adminId, adminName, images);
            if (updated == 0)
            {
                // 并发下另一位管理员刚写入第2位
                Map<String, Object> latest = signInMapper.selectReviewInfo(scheduleId, classDate);
                Object latest2 = latest == null ? null : latest.get("review2_admin_id");
                if (latest2 != null && Long.valueOf(String.valueOf(latest2)).equals(adminId))
                {
                    signInMapper.updateReview2Images(scheduleId, classDate, adminId, adminName, images);
                }
                else
                {
                    throw new ServiceException("该课次复核已完成");
                }
            }
        }
        else if (review2Admin.equals(adminId))
        {
            // 本人已确认过，仅更新图片
            signInMapper.updateReview2Images(scheduleId, classDate, adminId, adminName, images);
        }
        else
        {
            throw new ServiceException("该课次复核已完成");
        }

        Map<String, Object> latest = signInMapper.selectReviewInfo(scheduleId, classDate);
        int confirmed = 0;
        if (latest.get("review1_admin_id") != null)
        {
            confirmed++;
        }
        if (latest.get("review2_admin_id") != null)
        {
            confirmed++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("confirmedCount", confirmed);
        result.put("reviewStatus", "1".equals(String.valueOf(latest.get("review_status"))) ? "1" : "0");
        return result;
    }
}

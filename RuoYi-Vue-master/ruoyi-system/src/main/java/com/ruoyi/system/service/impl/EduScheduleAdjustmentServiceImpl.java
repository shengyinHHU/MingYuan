package com.ruoyi.system.service.impl;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.system.mapper.EduScheduleAdjustmentMapper;
import com.ruoyi.system.domain.EduScheduleAdjustment;
import com.ruoyi.system.domain.EduScheduleAdjustmentBatch;
import com.ruoyi.system.service.IEduScheduleAdjustmentService;

/**
 * 排课调课记录Service业务层处理
 *
 * @author ruoyi
 * @date 2026-08-26
 */
@Service
public class EduScheduleAdjustmentServiceImpl implements IEduScheduleAdjustmentService
{
    @Autowired
    private EduScheduleAdjustmentMapper eduScheduleAdjustmentMapper;
    @Autowired private com.ruoyi.system.mapper.EduCourseScheduleMapper singleSchedules;
    @Autowired private com.ruoyi.system.shop.ShopRepository singleDb;

    private void protectSingle(Long scheduleId) {
        var schedule = singleSchedules.lockSchedule(scheduleId);
        if (schedule != null && "2".equals(schedule.getClassMode()) && !singleDb.rows("SELECT enrollment_id FROM edu_enrollment WHERE schedule_id=? AND class_date IS NOT NULL FOR UPDATE",scheduleId).isEmpty()) throw new ServiceException("一对一已有按日期报名，不能通过批量调课改变原课次；请先核对处理历史");
    }

    /**
     * 查询排课调课记录
     *
     * @param adjustmentId 排课调课记录主键
     * @return 排课调课记录
     */
    @Override
    public EduScheduleAdjustment selectEduScheduleAdjustmentByAdjustmentId(Long adjustmentId)
    {
        return eduScheduleAdjustmentMapper.selectEduScheduleAdjustmentByAdjustmentId(adjustmentId);
    }

    /**
     * 查询排课调课记录列表
     *
     * @param eduScheduleAdjustment 排课调课记录
     * @return 排课调课记录
     */
    @Override
    public List<EduScheduleAdjustment> selectEduScheduleAdjustmentList(EduScheduleAdjustment eduScheduleAdjustment)
    {
        return eduScheduleAdjustmentMapper.selectEduScheduleAdjustmentList(eduScheduleAdjustment);
    }

    /**
     * 新增排课调课记录
     *
     * @param eduScheduleAdjustment 排课调课记录
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor=Exception.class)
    public int insertEduScheduleAdjustment(EduScheduleAdjustment eduScheduleAdjustment)
    {
        protectSingle(eduScheduleAdjustment.getScheduleId());
        eduScheduleAdjustment.setCreateTime(DateUtils.getNowDate());
        return eduScheduleAdjustmentMapper.insertEduScheduleAdjustment(eduScheduleAdjustment);
    }

    /**
     * 修改排课调课记录
     *
     * @param eduScheduleAdjustment 排课调课记录
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor=Exception.class)
    public int updateEduScheduleAdjustment(EduScheduleAdjustment eduScheduleAdjustment)
    {
        var current = eduScheduleAdjustmentMapper.selectEduScheduleAdjustmentByAdjustmentId(eduScheduleAdjustment.getAdjustmentId());
        if (current == null) throw new ServiceException("调课记录不存在");
        protectSingle(current.getScheduleId());
        if (eduScheduleAdjustment.getScheduleId() != null && !eduScheduleAdjustment.getScheduleId().equals(current.getScheduleId())) protectSingle(eduScheduleAdjustment.getScheduleId());
        eduScheduleAdjustment.setUpdateTime(DateUtils.getNowDate());
        return eduScheduleAdjustmentMapper.updateEduScheduleAdjustment(eduScheduleAdjustment);
    }

    /**
     * 批量删除排课调课记录
     *
     * @param adjustmentIds 需要删除的排课调课记录主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor=Exception.class)
    public int deleteEduScheduleAdjustmentByIds(Long[] adjustmentIds)
    {
        for (Long id : java.util.Arrays.stream(adjustmentIds).distinct().sorted().toList()) { var record = eduScheduleAdjustmentMapper.selectEduScheduleAdjustmentByAdjustmentId(id); if(record != null) protectSingle(record.getScheduleId()); }
        return eduScheduleAdjustmentMapper.deleteEduScheduleAdjustmentByIds(adjustmentIds);
    }

    /**
     * 删除排课调课记录信息
     *
     * @param adjustmentId 排课调课记录主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor=Exception.class)
    public int deleteEduScheduleAdjustmentByAdjustmentId(Long adjustmentId)
    {
        return deleteEduScheduleAdjustmentByIds(new Long[] {adjustmentId});
    }

    /**
     * 批量调课：按学期+期次（可叠加年级/科目筛选）把原日期范围内的全部上课日
     * 统一调整到新日期或停课，逐排课×逐上课日生成调课记录
     *
     * @param batch 批量调课参数
     * @return 生成的调课记录条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchAdjust(EduScheduleAdjustmentBatch batch)
    {
        if (batch == null || StringUtils.isEmpty(batch.getTermName()) || StringUtils.isEmpty(batch.getPeriodName()))
        {
            throw new ServiceException("请选择学期和期次");
        }
        if (batch.getOriginalStartDate() == null || batch.getOriginalEndDate() == null)
        {
            throw new ServiceException("请选择原上课日期范围");
        }
        if (batch.getOriginalStartDate().after(batch.getOriginalEndDate()))
        {
            throw new ServiceException("原上课日期范围不正确：开始日期晚于结束日期");
        }
        String mode = batch.getAdjustMode();
        if (!"MOVE_TO".equals(mode) && !"POSTPONE".equals(mode) && !"CANCEL".equals(mode))
        {
            throw new ServiceException("请选择调整方式");
        }
        if ("MOVE_TO".equals(mode) && batch.getTargetDate() == null)
        {
            throw new ServiceException("请选择调整后的目标日期");
        }
        if ("POSTPONE".equals(mode) && (batch.getOffsetDays() == null || batch.getOffsetDays() < 1))
        {
            throw new ServiceException("请填写顺延天数（至少1天）");
        }

        List<Map<String, Object>> schedules = eduScheduleAdjustmentMapper.selectSchedulesForBatch(
                batch.getTermName(), batch.getPeriodName(), batch.getGradeName(), batch.getSubjectName());
        if (schedules == null || schedules.isEmpty())
        {
            throw new ServiceException("未找到符合条件的排课，请检查学期/期次/年级/科目");
        }

        LocalDate rangeStart = toLocalDate(batch.getOriginalStartDate());
        LocalDate rangeEnd = toLocalDate(batch.getOriginalEndDate());
        String username = SecurityUtils.getUsername();
        Date now = DateUtils.getNowDate();
        int count = 0;

        for (Map<String, Object> s : schedules)
        {
            Long scheduleId = ((Number) s.get("scheduleId")).longValue();
            protectSingle(scheduleId);
            Set<LocalDate> classDates = computeClassDates(
                    toLocalDate((Date) s.get("startDate")),
                    toLocalDate((Date) s.get("endDate")),
                    (String) s.get("classPattern"),
                    (String) s.get("periodName"));
            if (classDates.isEmpty())
            {
                continue;
            }
            // 已被调过的原日期自动跳过，避免重复调课
            Set<LocalDate> alreadyAdjusted = eduScheduleAdjustmentMapper.selectAdjustedOriginalDates(scheduleId)
                    .stream().map(this::toLocalDate).collect(Collectors.toSet());
            List<LocalDate> affected = classDates.stream()
                    .filter(d -> !d.isBefore(rangeStart) && !d.isAfter(rangeEnd))
                    .filter(d -> !alreadyAdjusted.contains(d))
                    .sorted()
                    .collect(Collectors.toList());
            if (affected.isEmpty())
            {
                continue;
            }
            LocalDate first = affected.get(0);
            LocalDate target = "MOVE_TO".equals(mode) ? toLocalDate(batch.getTargetDate()) : null;
            for (LocalDate d : affected)
            {
                EduScheduleAdjustment adj = new EduScheduleAdjustment();
                adj.setScheduleId(scheduleId);
                adj.setOriginalDate(toDate(d));
                if ("CANCEL".equals(mode))
                {
                    // 停课：调整后日期留空
                    adj.setAdjustedDate(null);
                }
                else if ("POSTPONE".equals(mode))
                {
                    adj.setAdjustedDate(toDate(d.plusDays(batch.getOffsetDays())));
                }
                else
                {
                    // MOVE_TO：范围内第一个上课日调到目标日期，其余保持原有间隔依次顺移
                    adj.setAdjustedDate(toDate(target.plusDays(ChronoUnit.DAYS.between(first, d))));
                }
                adj.setReason(batch.getReason());
                adj.setCreateBy(username);
                adj.setCreateTime(now);
                count += eduScheduleAdjustmentMapper.insertEduScheduleAdjustment(adj);
            }
        }
        if (count == 0)
        {
            throw new ServiceException("日期范围内没有需要调整的上课日（可能均已被调整过）");
        }
        return count;
    }

    /**
     * 计算某排课在开课~结课日期内的全部上课日
     * 规则与小程序端保持一致：WEEKLY 按明确的周一至周日对齐；
     * 模式为空时期次为周六/周日按 WEEKLY，否则按 DAILY_5_1（上5天休1天）
     */
    private Set<LocalDate> computeClassDates(LocalDate start, LocalDate end, String classPattern, String periodName)
    {
        Set<LocalDate> dates = new HashSet<>();
        if (start == null || end == null || start.isAfter(end))
        {
            return dates;
        }
        String pattern = classPattern;
        if (StringUtils.isEmpty(pattern))
        {
            pattern = ("周六".equals(periodName) || "周日".equals(periodName)) ? "WEEKLY" : "DAILY_5_1";
        }
        if ("WEEKLY".equals(pattern))
        {
            int weekday = com.ruoyi.system.service.ScheduleCalendar.weekday(periodName);
            if (weekday == 0) return dates;
            DayOfWeek targetDay = DayOfWeek.of(weekday);
            LocalDate cur = start;
            while (!cur.isAfter(end) && cur.getDayOfWeek() != targetDay)
            {
                cur = cur.plusDays(1);
            }
            while (!cur.isAfter(end))
            {
                dates.add(cur);
                cur = cur.plusWeeks(1);
            }
        }
        else
        {
            LocalDate cur = start;
            int i = 0;
            while (!cur.isAfter(end))
            {
                if (i % 6 < 5)
                {
                    dates.add(cur);
                }
                cur = cur.plusDays(1);
                i++;
            }
        }
        return dates;
    }

    private LocalDate toLocalDate(Date date)
    {
        // 注意：MyBatis返回的DATE字段是java.sql.Date，其toInstant()会抛异常，统一改用毫秒值转换
        return date == null ? null : Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Date toDate(LocalDate localDate)
    {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}

package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.EduTeacherSalaryConfig;
import com.ruoyi.system.domain.EduTeacherSalaryRecord;
import com.ruoyi.system.mapper.EduTeacherSalaryConfigMapper;
import com.ruoyi.system.mapper.EduTeacherSalaryRecordMapper;
import com.ruoyi.system.service.IEduTeacherSalaryRecordService;

/**
 * 教师薪资结算 服务实现
 *
 * 课时口径：
 * - 班课：老师提交签到且该课次实到人数（到课1/试听4/调课5）≥1，计 1 次课，时长取排课时长（默认2小时）
 * - 一对一：按实到学员人次计，每人次 1 次课，时长取排课时长（默认配置时长）
 * - 学段：初一~初三=初中，高一~高三=高中，其余=小学
 *
 * @author ruoyi
 * @date 2026-09-04
 */
@Service
public class EduTeacherSalaryRecordServiceImpl implements IEduTeacherSalaryRecordService
{
    @Autowired
    private EduTeacherSalaryRecordMapper recordMapper;

    @Autowired
    private EduTeacherSalaryConfigMapper configMapper;

    /** 五类费用下标：0小学班课 1初高班课 2小学一对一 3初中一对一 4高中一对一 */
    private static final int PRIMARY_CLASS = 0;
    private static final int JH_CLASS = 1;
    private static final int PRIMARY_1ON1 = 2;
    private static final int JUNIOR_1ON1 = 3;
    private static final int SENIOR_1ON1 = 4;

    @Override
    public List<EduTeacherSalaryRecord> selectRecordList(EduTeacherSalaryRecord query)
    {
        return recordMapper.selectRecordList(query);
    }

    @Override
    public EduTeacherSalaryRecord selectRecordById(Long recordId)
    {
        return recordMapper.selectRecordById(recordId);
    }

    @Override
    public List<EduTeacherSalaryRecord> previewMonth(String salaryMonth)
    {
        checkMonth(salaryMonth);
        List<EduTeacherSalaryRecord> list = computeMonth(salaryMonth);
        // 标注已结算状态
        for (EduTeacherSalaryRecord r : list)
        {
            EduTeacherSalaryRecord saved = recordMapper.selectRecordByTeacherMonth(r.getTeacherId(), salaryMonth);
            if (saved != null)
            {
                r.setRecordId(saved.getRecordId());
                r.setConfirmStatus(saved.getConfirmStatus());
                r.setConfirmBy(saved.getConfirmBy());
                r.setConfirmTime(saved.getConfirmTime());
            }
            else
            {
                r.setConfirmStatus("0");
            }
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateMonth(String salaryMonth)
    {
        checkMonth(salaryMonth);
        // 已确认结算单锁定：存在已确认记录则拒绝整月重算
        EduTeacherSalaryRecord q = new EduTeacherSalaryRecord();
        q.setSalaryMonth(salaryMonth);
        List<EduTeacherSalaryRecord> existing = recordMapper.selectRecordList(q);
        for (EduTeacherSalaryRecord e : existing)
        {
            if ("1".equals(e.getConfirmStatus()))
            {
                throw new ServiceException(salaryMonth + " 存在已确认的结算单（" + e.getTeacherName()
                        + "），请先删除已确认单据再重新生成");
            }
        }
        List<EduTeacherSalaryRecord> list = computeMonth(salaryMonth);
        String username = SecurityUtils.getUsername();
        int count = 0;
        for (EduTeacherSalaryRecord r : list)
        {
            r.setCreateBy(username);
            EduTeacherSalaryRecord saved = recordMapper.selectRecordByTeacherMonth(r.getTeacherId(), salaryMonth);
            if (saved == null)
            {
                recordMapper.insertRecord(r);
            }
            else
            {
                r.setRecordId(saved.getRecordId());
                r.setUpdateBy(username);
                // 重算覆盖统计数据，保持待确认
                r.setConfirmStatus("0");
                recordMapper.updateRecord(r);
            }
            count++;
        }
        return count;
    }

    @Override
    public int confirmRecord(Long recordId)
    {
        EduTeacherSalaryRecord r = recordMapper.selectRecordById(recordId);
        if (r == null || "2".equals(r.getDelFlag()))
        {
            throw new ServiceException("结算单不存在或已删除");
        }
        if ("1".equals(r.getConfirmStatus()))
        {
            throw new ServiceException("该结算单已确认，无需重复确认");
        }
        EduTeacherSalaryRecord up = new EduTeacherSalaryRecord();
        up.setRecordId(recordId);
        up.setConfirmStatus("1");
        up.setConfirmBy(SecurityUtils.getUsername());
        up.setConfirmTime(DateUtils.getNowDate());
        up.setUpdateBy(SecurityUtils.getUsername());
        return recordMapper.updateRecord(up);
    }

    @Override
    public int deleteRecordByIds(Long[] recordIds)
    {
        for (Long id : recordIds)
        {
            EduTeacherSalaryRecord r = recordMapper.selectRecordById(id);
            if (r != null && "1".equals(r.getConfirmStatus()))
            {
                throw new ServiceException((r.getTeacherName() == null ? "" : r.getTeacherName())
                        + " 的结算单已确认，请先取消确认再删除");
            }
        }
        return recordMapper.deleteRecordByIds(recordIds);
    }

    /**
     * 按月实时计算每个教师的课时与费用（未入库）。
     * 范围：有薪资标准或当月有签到课次的教师。
     */
    private List<EduTeacherSalaryRecord> computeMonth(String salaryMonth)
    {
        // 教师账号
        List<Map<String, Object>> teacherUsers = recordMapper.selectTeacherUsers();
        Map<Long, String> teacherNames = new HashMap<>();
        for (Map<String, Object> u : teacherUsers)
        {
            teacherNames.put(toLong(u.get("teacherId")), String.valueOf(u.get("teacherName")));
        }
        // 薪资标准
        Map<Long, EduTeacherSalaryConfig> configMap = new HashMap<>();
        for (EduTeacherSalaryConfig c : configMapper.selectConfigList(new EduTeacherSalaryConfig()))
        {
            configMap.put(c.getTeacherId(), c);
        }
        // 当月课次聚合
        List<Map<String, Object>> sessions = recordMapper.selectMonthlySessions(salaryMonth);
        Map<Long, double[]> hoursMap = new HashMap<>();   // [5] 小时
        Map<Long, long[]> countMap = new HashMap<>();     // [5] 节数/人次
        Set<Long> involved = new HashSet<>();
        for (Map<String, Object> s : sessions)
        {
            Long teacherId = toLong(s.get("teacherId"));
            if (teacherId == null)
            {
                continue;
            }
            involved.add(teacherId);
            EduTeacherSalaryConfig cfg = configMap.get(teacherId);
            double defHours = cfg != null && cfg.getClassHoursPerSession() != null
                    ? cfg.getClassHoursPerSession().doubleValue() : 2.0;
            double sessionHours = sessionHours(s.get("startTime"), s.get("endTime"), defHours);
            long attended = toLong(s.get("attendedRows")) == null ? 0L : toLong(s.get("attendedRows"));
            String classMode = s.get("classMode") == null ? "1" : String.valueOf(s.get("classMode"));
            String stage = s.get("stage") == null ? "primary" : String.valueOf(s.get("stage"));

            int idx;
            long addCount;
            double addHours;
            if ("2".equals(classMode))
            {
                // 一对一：按实到学员人次
                if (attended <= 0)
                {
                    continue;
                }
                idx = "junior".equals(stage) ? JUNIOR_1ON1 : ("senior".equals(stage) ? SENIOR_1ON1 : PRIMARY_1ON1);
                addCount = attended;
                addHours = attended * sessionHours;
            }
            else
            {
                // 班课：实到≥1 计 1 次课
                if (attended <= 0)
                {
                    continue;
                }
                idx = "primary".equals(stage) ? PRIMARY_CLASS : JH_CLASS;
                addCount = 1;
                addHours = sessionHours;
            }
            hoursMap.computeIfAbsent(teacherId, k -> new double[5]);
            countMap.computeIfAbsent(teacherId, k -> new long[5]);
            hoursMap.get(teacherId)[idx] += addHours;
            countMap.get(teacherId)[idx] += addCount;
        }

        // 汇总：有标准或有课次的教师
        Set<Long> all = new HashSet<>();
        all.addAll(configMap.keySet());
        all.addAll(involved);
        List<EduTeacherSalaryRecord> result = new ArrayList<>();
        for (Long teacherId : all)
        {
            EduTeacherSalaryConfig cfg = configMap.get(teacherId);
            double[] hours = hoursMap.getOrDefault(teacherId, new double[5]);
            long[] counts = countMap.getOrDefault(teacherId, new long[5]);
            BigDecimal[] rates = new BigDecimal[] {
                rate(cfg, c -> c.getRatePrimaryClass()),
                rate(cfg, c -> c.getRateJhClass()),
                rate(cfg, c -> c.getRatePrimary1on1()),
                rate(cfg, c -> c.getRateJunior1on1()),
                rate(cfg, c -> c.getRateSenior1on1())
            };
            BigDecimal[] hoursBd = new BigDecimal[5];
            BigDecimal[] amountBd = new BigDecimal[5];
            long totalLessons = 0;
            BigDecimal totalHours = BigDecimal.ZERO;
            BigDecimal lessonAmount = BigDecimal.ZERO;
            for (int i = 0; i < 5; i++)
            {
                hoursBd[i] = BigDecimal.valueOf(hours[i]).setScale(1, RoundingMode.HALF_UP);
                amountBd[i] = hoursBd[i].multiply(rates[i]).setScale(2, RoundingMode.HALF_UP);
                totalLessons += counts[i];
                totalHours = totalHours.add(hoursBd[i]);
                lessonAmount = lessonAmount.add(amountBd[i]);
            }
            BigDecimal base = cfg != null && cfg.getBaseSalary() != null ? cfg.getBaseSalary() : BigDecimal.ZERO;

            EduTeacherSalaryRecord r = new EduTeacherSalaryRecord();
            r.setTeacherId(teacherId);
            r.setTeacherName(teacherNames.getOrDefault(teacherId, cfg != null ? cfg.getTeacherName() : ("ID:" + teacherId)));
            r.setSalaryMonth(salaryMonth);
            r.setBaseSalary(base.setScale(2, RoundingMode.HALF_UP));
            r.setPrimaryClassCount(counts[PRIMARY_CLASS]);
            r.setPrimaryClassHours(hoursBd[PRIMARY_CLASS]);
            r.setPrimaryClassAmount(amountBd[PRIMARY_CLASS]);
            r.setJhClassCount(counts[JH_CLASS]);
            r.setJhClassHours(hoursBd[JH_CLASS]);
            r.setJhClassAmount(amountBd[JH_CLASS]);
            r.setPrimary1on1Count(counts[PRIMARY_1ON1]);
            r.setPrimary1on1Hours(hoursBd[PRIMARY_1ON1]);
            r.setPrimary1on1Amount(amountBd[PRIMARY_1ON1]);
            r.setJunior1on1Count(counts[JUNIOR_1ON1]);
            r.setJunior1on1Hours(hoursBd[JUNIOR_1ON1]);
            r.setJunior1on1Amount(amountBd[JUNIOR_1ON1]);
            r.setSenior1on1Count(counts[SENIOR_1ON1]);
            r.setSenior1on1Hours(hoursBd[SENIOR_1ON1]);
            r.setSenior1on1Amount(amountBd[SENIOR_1ON1]);
            r.setTotalLessons(totalLessons);
            r.setTotalHours(totalHours.setScale(1, RoundingMode.HALF_UP));
            r.setLessonAmount(lessonAmount);
            r.setTotalAmount(base.add(lessonAmount).setScale(2, RoundingMode.HALF_UP));
            r.setDelFlag("0");
            result.add(r);
        }
        result.sort((a, b) -> a.getTeacherId().compareTo(b.getTeacherId()));
        return result;
    }

    /** 课次时长（小时）：优先取排课起止时间差，异常时用默认时长 */
    private double sessionHours(Object startObj, Object endObj, double defaultHours)
    {
        if (startObj instanceof Date && endObj instanceof Date)
        {
            long ms = ((Date) endObj).getTime() - ((Date) startObj).getTime();
            double h = ms / 3600000.0;
            if (h > 0 && h <= 6)
            {
                return h;
            }
        }
        return defaultHours;
    }

    private BigDecimal rate(EduTeacherSalaryConfig cfg, java.util.function.Function<EduTeacherSalaryConfig, BigDecimal> getter)
    {
        BigDecimal v = cfg == null ? null : getter.apply(cfg);
        return v == null ? BigDecimal.ZERO : v;
    }

    private Long toLong(Object o)
    {
        if (o == null)
        {
            return null;
        }
        if (o instanceof Number)
        {
            return ((Number) o).longValue();
        }
        return Long.valueOf(String.valueOf(o));
    }

    private void checkMonth(String salaryMonth)
    {
        if (salaryMonth == null || !salaryMonth.matches("^\\d{4}-\\d{2}$"))
        {
            throw new ServiceException("月份格式应为 yyyy-MM");
        }
    }
}

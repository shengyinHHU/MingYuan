package com.ruoyi.system.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 按已有日期模式展开排课；日期或每周星期不明确时返回空，不能用于推断空闲。 */
public final class ScheduleCalendar {
    private ScheduleCalendar() {}
    public static int weekday(String period) {
        return List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日").indexOf(period == null ? "" : period) + 1;
    }
    public static SortedSet<LocalDate> dates(LocalDate start, LocalDate end, String pattern,
                                            String period, List<Map<String, Object>> adjustments) {
        SortedSet<LocalDate> dates = new TreeSet<>();
        if (start == null || end == null || start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 3660) return dates;
        boolean weekend = "周六".equals(period) || "周日".equals(period);
        int weekday = weekday(period);
        if (pattern == null || pattern.isBlank()) pattern = weekend ? "WEEKLY" : "";
        if (!"DAILY_5_1".equals(pattern) && !("WEEKLY".equals(pattern) && weekday > 0)) return dates;
        Map<LocalDate, LocalDate> moved = new HashMap<>();
        for (Map<String, Object> adjustment : adjustments) {
            LocalDate original = date(adjustment.get("originalDate"));
            Object adjustedValue = adjustment.get("adjustedDate");
            LocalDate adjusted = date(adjustedValue);
            if (original == null || (adjustedValue != null && adjusted == null) || moved.containsKey(original)) return new TreeSet<>();
            moved.put(original, adjusted);
        }
        int index = 0;
        for (LocalDate current = start; !current.isAfter(end); current = current.plusDays(1), index++) {
            boolean included = "WEEKLY".equals(pattern)
                ? current.getDayOfWeek().getValue() == weekday : index % 6 < 5;
            if (!included) continue;
            LocalDate actual = moved.containsKey(current) ? moved.get(current) : current;
            if (actual != null) dates.add(actual);
        }
        return dates;
    }
    public static LocalDate date(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (value instanceof java.util.Date date) return date.toInstant().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
        try { return LocalDate.parse(String.valueOf(value).substring(0, 10)); }
        catch (RuntimeException e) { return null; }
    }
}

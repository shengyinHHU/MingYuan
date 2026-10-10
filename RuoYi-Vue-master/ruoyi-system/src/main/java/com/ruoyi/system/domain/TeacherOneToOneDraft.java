package com.ruoyi.system.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.ruoyi.common.exception.ServiceException;

/** 教师简化创建参数；教师等级不接受客户端提供。复用现有排课表。 */
public class TeacherOneToOneDraft {
    public String lessonLocation;
    public String gradeName;
    public String courseClassName;

    public LocalDate startDate;
    public LocalTime startTime;
    public Integer weeks;

    public EduCourseSchedule toSchedule(String configuredSubject) {
        if (startDate == null || startTime == null || weeks == null || weeks < 1 || weeks > 52)
            throw new ServiceException("请选择开始日期、时间和1至52周的持续周数");
        if (startTime.getSecond() != 0 || !startTime.isBefore(LocalTime.of(22, 0)))
            throw new ServiceException("开始时间需精确到分钟，且早于22:00；暂不支持跨午夜排课");
        EduCourseSchedule s = new EduCourseSchedule();
        if (lessonLocation == null || lessonLocation.isBlank() || lessonLocation.length() > 255) throw new ServiceException("请填写255字以内的上课地点");
        s.setClassroomId(null); s.setLessonLocation(lessonLocation.trim()); s.setGradeName(gradeName); s.setSubjectName(configuredSubject);
        s.setCourseClassName(courseClassName == null ? null : courseClassName.trim());
        s.setClassMode("2"); s.setClassType("一对一"); s.setStatus("0"); s.setRecruitStatus("0");
        s.setCourseYear((long) startDate.getYear());
        // 按首课日期区分既有若依网格定位，避免不同日期的同一时段被唯一键误判。
        s.setTermName("一对一" + startDate);
        s.setPeriodName(List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日").get(startDate.getDayOfWeek().getValue() - 1));
        s.setStartTime(startTime); s.setEndTime(startTime.plusHours(2));
        s.setTimeSlot(startTime.toString() + "-" + startTime.plusHours(2));
        s.setStartDate(java.sql.Date.valueOf(startDate));
        s.setEndDate(java.sql.Date.valueOf(startDate.plusWeeks(weeks - 1)));
        s.setClassPattern("WEEKLY");
        return s;
    }
}

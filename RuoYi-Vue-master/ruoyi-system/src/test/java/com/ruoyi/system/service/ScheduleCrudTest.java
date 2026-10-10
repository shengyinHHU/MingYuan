package com.ruoyi.system.service;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.core.domain.entity.*;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.EduCourseSchedule;
import com.ruoyi.system.mapper.*;
import com.ruoyi.system.service.impl.EduCourseScheduleServiceImpl;
import com.ruoyi.system.shop.ShopRepository;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;

/** 不写业务数据库；验证共享服务的归属、历史保护及真实 MyBatis 动态 SQL。 */
public class ScheduleCrudTest {
    EduCourseScheduleServiceImpl service;
    FakeDb db;
    EduCourseSchedule current, captured;
    Long[] deleted;
    @BeforeEach void setup() throws Exception {
        principal(21, "teacher"); db = new FakeDb(); current = course(); current.setScheduleId(10L);
        service = new EduCourseScheduleServiceImpl();
        inject(service, "db", db);
        inject(service, "singleService", new OneToOneEnrollmentService() { @Override public String subject(Long id) { return "数学"; } });
        inject(service, "parentMapper", proxy(MiniAppParentMapper.class, (p,m,a) -> List.of()));
        inject(service, "eduCourseScheduleMapper", proxy(EduCourseScheduleMapper.class, (p,m,a) -> {
            switch(m.getName()) {
                case "selectEduCourseScheduleByScheduleId": case "lockSchedule":
                    if (Long.valueOf(10).equals(a[0])) return current;
                    EduCourseSchedule foreign = course(); foreign.setScheduleId(20L); foreign.setTeacherId(22L); return foreign;
                case "insertEduCourseSchedule": case "updateEduCourseSchedule": captured = (EduCourseSchedule)a[0]; return 1;
                case "deleteEduCourseScheduleByScheduleIds": deleted = (Long[])a[0]; return deleted.length;
                case "selectEduCourseScheduleList": case "selectTimetableGrid": captured = (EduCourseSchedule)a[0]; return List.of();
                default: throw new AssertionError(m.getName());
            }
        }));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void teacherReadAndWriteIsolation() {
        EduCourseSchedule query = new EduCourseSchedule(); query.getParams().put("teacherUserId", 22L);
        service.selectEduCourseScheduleList(query); assertEquals(21L, captured.getParams().get("teacherUserId"));
        assertThrows(ServiceException.class, () -> service.selectEduCourseScheduleByScheduleId(20L));
        EduCourseSchedule foreign = course(); foreign.setTeacherId(22L);
        assertThrows(ServiceException.class, () -> service.insertEduCourseSchedule(foreign));
        assertThrows(ServiceException.class, () -> service.deleteEduCourseScheduleByScheduleIds(new Long[]{10L,20L}));
        assertNull(deleted);
    }
    @Test void simpleDraftUsesExactWeeksWeekdayAndTwoHours() {
        var draft = new com.ruoyi.system.domain.TeacherOneToOneDraft();
        draft.lessonLocation = "校外自定义地点"; draft.gradeName = "一年级"; draft.courseClassName = " 一对一数学 ";
        draft.startDate = LocalDate.parse("2026-12-28"); draft.startTime = LocalTime.of(8,30); draft.weeks = 4;
        EduCourseSchedule schedule = draft.toSchedule("数学");
        assertNull(schedule.getClassroomId()); assertEquals("校外自定义地点",schedule.getLessonLocation());
        assertEquals("周一", schedule.getPeriodName()); assertEquals(LocalTime.of(10,30), schedule.getEndTime());
        assertEquals(LocalDate.parse("2027-01-18"), ScheduleCalendar.date(schedule.getEndDate()));
        assertEquals(4, ScheduleCalendar.dates(draft.startDate, ScheduleCalendar.date(schedule.getEndDate()), schedule.getClassPattern(), schedule.getPeriodName(), List.of()).size());
        assertEquals(1, service.insertEduCourseSchedule(schedule));
        draft.weeks = 1; assertEquals(draft.startDate, ScheduleCalendar.date(draft.toSchedule("数学").getEndDate()));
        draft.weeks = 0; assertThrows(ServiceException.class, () -> draft.toSchedule("数学"));
        draft.weeks = 53; assertThrows(ServiceException.class, () -> draft.toSchedule("数学"));
        draft.weeks = 2; draft.startTime = LocalTime.of(22,0); assertThrows(ServiceException.class, () -> draft.toSchedule("数学"));
        draft.startTime = LocalTime.of(21,59); assertEquals(LocalTime.of(23,59), draft.toSchedule("数学").getEndTime());
    }
    @Test void teacherCanOnlyCreateOneToOneButAdminCanCreateClass() {
        EduCourseSchedule input = course(); input.setClassMode("1");
        assertThrows(ServiceException.class, () -> service.insertEduCourseSchedule(input));
        input.setClassMode(null);
        assertThrows(ServiceException.class, () -> service.insertEduCourseSchedule(input));
        principal(21, "admin", "teacher"); input.setClassMode("1");
        assertEquals(1, service.insertEduCourseSchedule(input));
    }
    @Test void newCourseBindsIdAndProtectsCounters() {
        EduCourseSchedule input = course(); input.setTeacherName("forged"); input.setCreateBy("forged"); input.setEnrolledCount(99L);
        assertEquals(1, service.insertEduCourseSchedule(input));
        assertEquals(21L, captured.getTeacherId()); assertEquals("Teacher A", captured.getTeacherName());
        assertEquals("teacher21", captured.getCreateBy()); assertEquals(0L, captured.getEnrolledCount()); assertEquals("0", captured.getDelFlag());
        assertEquals(List.of("room:1", "teacher:21"), db.locks);
    }
    @Test void timeDateAndOneToOneValidation() {
        EduCourseSchedule bad = course(); bad.setEndTime(LocalTime.of(7,0));
        assertThrows(ServiceException.class, () -> service.insertEduCourseSchedule(bad));
        EduCourseSchedule duration = course(); duration.setClassMode("2"); duration.setEndTime(LocalTime.of(9,59)); assertThrows(ServiceException.class, () -> service.insertEduCourseSchedule(duration));
        EduCourseSchedule half = course(); half.setEndDate(null);
        assertThrows(ServiceException.class, () -> service.insertEduCourseSchedule(half));
        EduCourseSchedule unknown = course(); unknown.setPeriodName("一期");
        assertThrows(ServiceException.class, () -> service.insertEduCourseSchedule(unknown));
        EduCourseSchedule draft = course(); draft.setStartDate(null); draft.setEndDate(null); draft.setClassPattern(null);
        assertEquals(1, service.insertEduCourseSchedule(draft));
    }
    @Test void updateDoesNotOverwriteSeatsOrIdentity() {
        EduCourseSchedule input = new EduCourseSchedule(); input.setScheduleId(10L); input.setRemark("updated"); input.setEnrolledCount(42L); input.setDelFlag("2"); input.setCreateBy("forged");
        assertEquals(1, service.updateEduCourseSchedule(input));
        assertNull(captured.getEnrolledCount()); assertNull(captured.getDelFlag()); assertNull(captured.getCreateBy()); assertNull(captured.getScheduleCode());
        EduCourseSchedule other = new EduCourseSchedule(); other.setScheduleId(10L); other.setTeacherId(22L);
        assertThrows(ServiceException.class, () -> service.updateEduCourseSchedule(other));
    }
    @Test void attendanceHistoryAllowsStoppingButBlocksTimeRewrites() {
        db.attendance = 1;
        EduCourseSchedule changed = new EduCourseSchedule(); changed.setScheduleId(10L); changed.setEndTime(LocalTime.of(11,0));
        assertThrows(ServiceException.class, () -> service.updateEduCourseSchedule(changed));
        EduCourseSchedule stop = new EduCourseSchedule(); stop.setScheduleId(10L); stop.setStatus("1");
        assertEquals(1, service.updateEduCourseSchedule(stop));
    }
    @Test void deletionChecksAllReferencesAndDeduplicatesIds() {
        db.references = 1;
        assertThrows(ServiceException.class, () -> service.deleteEduCourseScheduleByScheduleIds(new Long[]{10L})); assertNull(deleted);
        db.references = 0;
        assertEquals(1, service.deleteEduCourseScheduleByScheduleIds(new Long[]{10L,10L})); assertArrayEquals(new Long[]{10L}, deleted);
    }
    @Test void conflictAndBoundaryChecksUseActualDates() {
        db.conflicts = List.of(Map.of("scheduleId","30","startTime","09:00:00","endTime","11:00:00","startDate","2026-10-01","endDate","2026-10-31","classPattern","WEEKLY","periodName","周六","courseClassName","other"));
        assertThrows(ServiceException.class, () -> service.insertEduCourseSchedule(course()));
        EduCourseSchedule adjacent = course(); adjacent.setStartTime(LocalTime.of(11,0)); adjacent.setEndTime(LocalTime.of(13,0));
        assertEquals(1, service.insertEduCourseSchedule(adjacent));
    }
    @Test void calendarRespectsMovesCancellationsAndMissingRules() {
        var dates = ScheduleCalendar.dates(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), "WEEKLY", "周六", List.of(Map.of("originalDate","2026-10-10","adjustedDate","2026-10-11"), new HashMap<>(Map.of("originalDate","2026-10-17"))));
        assertTrue(dates.contains(LocalDate.parse("2026-10-11"))); assertFalse(dates.contains(LocalDate.parse("2026-10-10"))); assertFalse(dates.contains(LocalDate.parse("2026-10-17")));
        assertTrue(ScheduleCalendar.dates(null, LocalDate.now(), "WEEKLY", "周六", List.of()).isEmpty());
        assertTrue(ScheduleCalendar.dates(LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-31"),"WEEKLY","一期",List.of()).isEmpty());
    }
    @Test void mapperInsertValuesMatchAndTimeUsesLocalTime() throws Exception {
        Configuration cfg = new Configuration(); cfg.getTypeAliasRegistry().registerAlias("EduCourseSchedule", EduCourseSchedule.class);
        String path = "mapper/system/EduCourseScheduleMapper.xml";
        try(var in = EduCourseScheduleMapper.class.getClassLoader().getResourceAsStream(path)) { new XMLMapperBuilder(in,cfg,path,cfg.getSqlFragments()).parse(); }
        EduCourseSchedule input = course(); input.setClassMode("2");
        var sql = cfg.getMappedStatement("com.ruoyi.system.mapper.EduCourseScheduleMapper.insertEduCourseSchedule").getBoundSql(input);
        String statement = sql.getSql().replaceAll("\\s+", " ");
        int columns = statement.substring(statement.indexOf('(')+1, statement.indexOf(')')).split(",").length;
        assertEquals(columns, sql.getParameterMappings().size());
        assertTrue(statement.contains("class_mode")); assertTrue(statement.contains("teacher_id"));
        assertTrue(sql.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals("classMode")));
        assertTrue(sql.getParameterMappings().stream().filter(p -> p.getProperty().equals("startTime")).allMatch(p -> p.getTypeHandler() instanceof org.apache.ibatis.type.LocalTimeTypeHandler));
        input.setScheduleId(10L); input.setEnrolledCount(null);
        assertFalse(cfg.getMappedStatement("com.ruoyi.system.mapper.EduCourseScheduleMapper.updateEduCourseSchedule").getBoundSql(input).getSql().contains("enrolled_count"));
    }
    static EduCourseSchedule course() {
        var s = new EduCourseSchedule(); s.setTeacherId(21L); s.setClassroomId(1L); s.setScheduleCode("TEST"); s.setCourseYear(2026L);
        s.setTermName("秋季"); s.setPeriodName("周六"); s.setTimeSlot("8:00-10:00"); s.setGradeName("一年级"); s.setSubjectName("数学"); s.setCourseClassName("班次");
        s.setClassMode("2"); s.setStatus("0"); s.setRecruitStatus("0"); s.setDelFlag("0");
        s.setStartTime(LocalTime.of(8,0)); s.setEndTime(LocalTime.of(10,0));
        s.setStartDate(java.sql.Date.valueOf("2026-10-01")); s.setEndDate(java.sql.Date.valueOf("2026-10-31")); s.setClassPattern("WEEKLY");
        return s;
    }
    static void principal(long id, String... keys) {
        var user = new SysUser(); user.setUserId(id); user.setUserName("teacher"+id); user.setNickName("Teacher A");
        user.setRoles(Arrays.stream(keys).map(key -> { var r = new SysRole(); r.setRoleKey(key); return r; }).toList());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(id,null,user,Set.of()),null,List.of()));
    }
    static void inject(Object target, String field, Object value) throws Exception { var f = target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target,value); }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, InvocationHandler handler) { return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler); }
    static class FakeDb extends ShopRepository {
        long attendance, references; List<Map<String,Object>> conflicts = List.of(); List<String> locks = new ArrayList<>();
        FakeDb() { super(new DriverManagerDataSource()); }
        @Override public List<Map<String,Object>> rows(String sql,Object... args) {
            if (sql.contains("SELECT u.nick_name")) return List.of(Map.of("nickName","Teacher A","userName","teacher21"));
            if (sql.contains("information_schema")) return List.of(Map.of("tableName","edu_homework"));
            if (sql.contains("SELECT schedule_id,start_time")) return conflicts;
            if (sql.contains("SELECT enrollment_id")) return List.of();
            if (sql.contains("SELECT sign_in_id")) return attendance > 0 ? List.of(Map.of("signInId",1)) : List.of();
            if (sql.contains("SELECT attendance_id")) return List.of();
            if (sql.contains("`edu_homework`")) return references > 0 ? List.of(Map.of("scheduleId",10)) : List.of();
            throw new AssertionError(sql);
        }
        @Override public Map<String,Object> one(String sql,Object... args) {
            if(sql.contains("FROM edu_classroom")) { if(sql.contains("FOR UPDATE")) locks.add("room:"+args[0]); return Map.of("status","0","capacity",30); }
            if(sql.contains("FOR UPDATE")) { locks.add("teacher:"+args[0]); return Map.of("userId",args[0]); }
            throw new AssertionError(sql);
        }
        @Override public JdbcTemplate jdbc() { return new JdbcTemplate() {
            @Override @SuppressWarnings("unchecked") public <T> T queryForObject(String sql,Class<T> type,Object... args) {
                long value = sql.startsWith("SELECT (SELECT COUNT") ? attendance : sql.contains("`edu_homework`") ? references : 0;
                return (T) Long.valueOf(value);
            }
        }; }
    }
    /** 离线执行与 JUnit 相同的场景，不需要启动业务数据库。 */
    public static void main(String[] args) throws Exception {
        int count = 0;
        for(Method method : ScheduleCrudTest.class.getDeclaredMethods()) if(method.isAnnotationPresent(Test.class)) {
            var test = new ScheduleCrudTest(); test.setup();
            try { method.invoke(test); count++; }
            catch(InvocationTargetException e) { throw new AssertionError(method.getName(),e.getCause()); }
            finally { test.cleanup(); }
        }
        System.out.println("PASS: " + count + " schedule CRUD/calendar/MyBatis scenarios (no business DB writes)");
    }
}

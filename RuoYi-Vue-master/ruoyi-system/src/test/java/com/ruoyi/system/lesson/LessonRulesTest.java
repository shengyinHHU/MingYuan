package com.ruoyi.system.lesson;

import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.common.exception.ServiceException;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;

class LessonRulesTest {
  Object rule(String name, Class<?>[] types, Object... args) throws Exception {
    Class<?> rules;
    try { rules = Class.forName("com.ruoyi.system.lesson.LessonRules"); }
    catch (ClassNotFoundException e) { fail("LessonRules must implement lesson trust-boundary rules"); return null; }
    try { return rules.getMethod(name, types).invoke(null, args); }
    catch (InvocationTargetException e) { throw (RuntimeException)e.getCause(); }
  }
  @Test void literalTeacherCostsAndUnknownLevelRejected() throws Exception {
    assertEquals(1, assertDoesNotThrow(() -> rule("cost", new Class[]{String.class}, "elite")));
    assertEquals(2, assertDoesNotThrow(() -> rule("cost", new Class[]{String.class}, "senior")));
    for (String invalid : new String[]{null,"","UNKNOWN","NORMAL","LEAD","Elite"}) {
      var error=assertThrows(ServiceException.class, () -> rule("cost", new Class[]{String.class}, invalid));
      assertTrue(error.getMessage().contains("管理员"));
    }
  }
  @Test void cancellationBoundaryUsesStrictlyEarlierServerTime() throws Exception {
    assertEquals(true, rule("beforeStart", new Class[]{Object.class,Object.class}, "2026-10-11 09:59:59", "2026-10-11 10:00:00"));
    assertEquals(false, rule("beforeStart", new Class[]{Object.class,Object.class}, "2026-10-11 10:00:00", "2026-10-11 10:00:00"));
  }
  @Test void exactlyTwoHoursAndIntegerUnitsRequired() throws Exception {
    assertDoesNotThrow(() -> rule("duration", new Class[]{Object.class,Object.class}, "2026-10-11 10:00:00", "2026-10-11 12:00:00"));
    assertThrows(ServiceException.class, () -> rule("duration", new Class[]{Object.class,Object.class}, "2026-10-11 10:00:00", "2026-10-11 12:00:01"));
    assertEquals(10, rule("units", new Class[]{Object.class}, "10"));
    assertThrows(ServiceException.class, () -> rule("units", new Class[]{Object.class}, "1.5"));
    assertThrows(ServiceException.class, () -> rule("units", new Class[]{Object.class}, "0"));
  }
  @Test void subjectUsesStableCode() throws Exception {
    assertDoesNotThrow(() -> rule("subject", new Class[]{String.class,String.class}, "math", "math"));
    assertThrows(ServiceException.class, () -> rule("subject", new Class[]{String.class,String.class}, "math", "数学"));
  }
}

package com.ruoyi.system.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class FinanceRulesTest {
  Object call(String method, Class<?>[] types, Object... args) throws Exception {
    Class<?> rules;
    try {
      rules = Class.forName("com.ruoyi.system.finance.FinanceRules");
    } catch (ClassNotFoundException e) {
      fail("Finance money and environment rules are not implemented");
      return null;
    }
    try {
      return rules.getMethod(method, types).invoke(null, args);
    } catch (InvocationTargetException e) {
      throw (Exception) e.getCause();
    }
  }

  @Test
  void amountsRejectRoundingAndNullInsteadOfTreatingUnknownAsFree() throws Exception {
    assertEquals(new BigDecimal("900.00"), call("money", new Class<?>[] {Object.class}, "900"));
    assertThrows(Exception.class, () -> call("money", new Class<?>[] {Object.class}, "1.001"));
    assertThrows(
        Exception.class, () -> call("money", new Class<?>[] {Object.class}, (Object) null));
    assertThrows(Exception.class, () -> call("money", new Class<?>[] {Object.class}, "-0.01"));
  }

  @Test
  void mixedProductionProfilesAlwaysExcludeMock() throws Exception {
    assertEquals(
        true,
        call(
            "mockAllowed",
            new Class<?>[] {String[].class, String.class},
            new String[] {"local"},
            "mock"));
    assertEquals(
        false,
        call(
            "mockAllowed",
            new Class<?>[] {String[].class, String.class},
            new String[] {"local", "prod"},
            "mock"));
    assertEquals(
        false,
        call(
            "mockAllowed",
            new Class<?>[] {String[].class, String.class},
            new String[] {"test", "production"},
            "mock"));
    assertEquals(
        false,
        call(
            "mockAllowed",
            new Class<?>[] {String[].class, String.class},
            new String[] {"local"},
            "disabled"));
  }

  @Test
  void requestHashIsStableForDifferentMapIterationOrders() throws Exception {
    var first = new LinkedHashMap<String, Object>();
    first.put("templateId", "9");
    first.put("parentIds", List.of(2L, 3L));
    first.put("reason", "活动");
    var second = new LinkedHashMap<String, Object>();
    second.put("reason", "活动");
    second.put("parentIds", List.of(2L, 3L));
    second.put("templateId", "9");
    assertEquals(
        call("hash", new Class<?>[] {Object.class}, first),
        call("hash", new Class<?>[] {Object.class}, second));
    second.put("parentIds", List.of(2L, 4L));
    assertNotEquals(
        call("hash", new Class<?>[] {Object.class}, first),
        call("hash", new Class<?>[] {Object.class}, second));
  }

  @Test
  void actorIdsRemainExactStringsInNestedFinanceDtos() throws Exception {
    var source =
        Map.<String, Object>of(
            "priceConfirmedBy",
            9007199254740993L,
            "financialVersion",
            2,
            "payments",
            List.of(Map.of("confirmedBy", 9007199254740993L, "recordedBy", 9007199254740994L)));
    assertTrue(
        Arrays.stream(Class.forName("com.ruoyi.system.finance.FinanceRules").getMethods())
            .anyMatch(m -> m.getName().equals("dto")),
        "Finance DTO ID serialization is missing");
    Map<?, ?> result = (Map<?, ?>) call("dto", new Class<?>[] {Map.class}, source);
    assertEquals("9007199254740993", result.get("priceConfirmedBy"));
    assertEquals(2, result.get("financialVersion"));
    Map<?, ?> payment = (Map<?, ?>) ((List<?>) result.get("payments")).get(0);
    assertEquals("9007199254740994", payment.get("recordedBy"));
  }
}

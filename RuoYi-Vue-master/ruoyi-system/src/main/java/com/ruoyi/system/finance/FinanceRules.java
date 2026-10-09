package com.ruoyi.system.finance;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Shared trust-boundary rules; unknown historical money must stay unknown. */
public final class FinanceRules {
  private FinanceRules() {}

  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public static String text(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  public static String str(Map<String, ?> m, String key) {
    return text(m.get(key));
  }

  public static void require(boolean ok, String message) {
    if (!ok) throw new ServiceException(message);
  }

  public static long id(Object v) {
    try {
      long n = Long.parseLong(text(v));
      if (n < 1) throw new IllegalArgumentException();
      return n;
    } catch (Exception e) {
      throw new ServiceException("ID无效");
    }
  }

  public static BigDecimal money(Object v) {
    try {
      BigDecimal n = new BigDecimal(text(v)).setScale(2, RoundingMode.UNNECESSARY);
      if (n.signum() < 0 || n.compareTo(new BigDecimal("99999999.99")) > 0)
        throw new ArithmeticException();
      return n;
    } catch (Exception e) {
      throw new ServiceException("金额必须为非负数且最多两位小数");
    }
  }

  public static boolean mockAllowed(String[] profiles, String mode) {
    return Arrays.stream(profiles).anyMatch(p -> p.equals("local") || p.equals("test"))
        && Arrays.stream(profiles).noneMatch(p -> p.equals("prod") || p.equals("production"))
        && "mock".equalsIgnoreCase(mode);
  }

  public static Timestamp now() {
    return Timestamp.valueOf(LocalDateTime.now(ZONE));
  }

  public static Timestamp time(Object value) {
    try {
      return Timestamp.valueOf(
          LocalDateTime.parse(text(value), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    } catch (Exception e) {
      throw new ServiceException("时间格式必须为yyyy-MM-dd HH:mm:ss");
    }
  }

  public static String code(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "");
  }

  public static String key(Map<String, Object> body) {
    String key = str(body, "idempotencyKey");
    require(key.matches("[A-Za-z0-9_-]{1,64}"), "请提供有效幂等请求编号");
    return key;
  }

  public static String reason(Map<String, Object> b, String name, int max) {
    String s = str(b, name);
    require(!s.isBlank() && s.length() <= max, "请填写有效的原因／说明（最多" + max + "字）");
    return s;
  }

  private static Object canonical(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sorted = new TreeMap<>();
      map.forEach((k, v) -> sorted.put(String.valueOf(k), canonical(v)));
      return sorted;
    }
    if (value instanceof Collection<?> items)
      return items.stream().map(FinanceRules::canonical).toList();
    return value;
  }

  public static String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(JSON.toJSONString(canonical(value)).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static Map<String, Object> dto(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (var entry : source.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Number
          && Set.of(
                  "recordedBy",
                  "confirmedBy",
                  "priceConfirmedBy",
                  "issuedBy",
                  "voidBy",
                  "grantedBy",
                  "revokedBy")
              .contains(entry.getKey())) value = value.toString();
      else if (value instanceof Map<?, ?> map) value = dto((Map<String, Object>) map);
      else if (value instanceof Collection<?> list)
        value =
            list.stream()
                .map(v -> v instanceof Map<?, ?> m ? dto((Map<String, Object>) m) : v)
                .toList();
      result.put(entry.getKey(), value);
    }
    return result;
  }

  public static String fingerprint(long business, Map<String, Object> b, String... keys) {
    Map<String, Object> normalized = new TreeMap<>();
    normalized.put("businessId", String.valueOf(business));
    for (String k : keys) normalized.put(k, text(b.get(k)));
    return hash(normalized);
  }

  public static Map<String, Object> values(Object... pairs) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }

  public static int integer(Object v, int min, int max) {
    try {
      int n = new BigDecimal(text(v)).intValueExact();
      if (n < min || n > max) throw new ArithmeticException();
      return n;
    } catch (Exception e) {
      throw new ServiceException("请输入范围内整数");
    }
  }
}

package com.ruoyi.system.lesson;

import static com.ruoyi.system.finance.FinanceRules.*;
import java.sql.Timestamp;
import java.time.Duration;

public final class LessonRules {
  private LessonRules() {}
  public static int cost(String level) {
    require("elite".equals(level)||"senior".equals(level),"教师等级未设置或无效，请联系管理员配置精英或资深等级");
    return "senior".equals(level)?2:1;
  }
  public static Timestamp timestamp(Object value) {return value instanceof Timestamp t?t:time(value);}
  public static boolean beforeStart(Object now,Object start) {return timestamp(now).before(timestamp(start));}
  public static void duration(Object start,Object end) {
    require(Duration.between(timestamp(start).toLocalDateTime(),timestamp(end).toLocalDateTime()).equals(Duration.ofHours(2)),"每次课固定两小时");
  }
  public static int units(Object value) {return integer(value,1,Integer.MAX_VALUE);}
  public static void subject(String actual,String expected) {require(!actual.isBlank()&&actual.equals(expected),"课包与时段学科不一致");}
}

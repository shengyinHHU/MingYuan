package com.ruoyi.system.finance;

import static com.ruoyi.system.finance.FinanceRules.*;

import com.ruoyi.system.shop.ShopRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CouponService {
  private final ShopRepository db;

  public CouponService(ShopRepository db) {
    this.db = db;
  }

  public void admin(long actor) {
    require(
        db.jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.user_id"
                        + " JOIN sys_role r ON r.role_id=ur.role_id WHERE u.user_id=? AND"
                        + " u.status='0' AND u.del_flag='0' AND r.role_key='admin' AND r.status='0'"
                        + " AND r.del_flag='0'",
                    Integer.class,
                    actor)
            > 0,
        "FORBIDDEN: 管理员权限不足");
  }

  public String username(long actor) {
    return str(db.one("SELECT user_name FROM sys_user WHERE user_id=?", actor), "userName");
  }

  public static Map<String, Object> page(
      ShopRepository db, String select, String count, List<Object> args, Map<String, String> q) {
    int number = integer(q.getOrDefault("pageNum", "1"), 1, 1000000),
        size = integer(q.getOrDefault("pageSize", "20"), 1, 100);
    long total =
        Objects.requireNonNull(db.jdbc().queryForObject(count, Long.class, args.toArray()));
    List<Object> paged = new ArrayList<>(args);
    paged.add(size);
    paged.add((number - 1) * size);
    return Map.of("rows", db.rows(select + " LIMIT ? OFFSET ?", paged.toArray()), "total", total);
  }

  public Map<String, Object> listTemplates(Map<String, String> q) {
    String where = " WHERE 1=1";
    List<Object> a = new ArrayList<>();
    if (!str(q, "couponName").isEmpty()) {
      where += " AND coupon_name LIKE ?";
      a.add("%" + str(q, "couponName") + "%");
    }
    if (!str(q, "templateStatus").isEmpty()) {
      where += " AND template_status=?";
      a.add(q.get("templateStatus"));
    }
    return page(
        db,
        "SELECT * FROM edu_coupon_template" + where + " ORDER BY template_id DESC",
        "SELECT COUNT(*) FROM edu_coupon_template" + where,
        a,
        q);
  }

  public Map<String, Object> saveTemplate(long actor, Long template, Map<String, Object> b) {
    admin(actor);
    return db.transactions()
        .execute(
            tx -> {
              Map<String, Object> old =
                  template == null
                      ? Map.of()
                      : db.one(
                          "SELECT * FROM edu_coupon_template WHERE template_id=? FOR UPDATE",
                          template);
              Map<String, Object> merged = new HashMap<>(old);
              merged.putAll(b);
              String name = reason(merged, "couponName", 100),
                  description = str(merged, "description");
              require(description.length() <= 500, "说明过长");
              BigDecimal discount = money(merged.get("discountAmount")),
                  min = money(merged.getOrDefault("minSpendAmount", "0"));
              require(discount.signum() > 0, "券面额必须大于0");
              Timestamp from = time(merged.get("validFrom")),
                  until = time(merged.get("validUntil"));
              require(until.after(from), "失效时间必须晚于生效时间");
              int total = integer(merged.get("totalQuantity"), 1, Integer.MAX_VALUE);
              String status = str(merged, "templateStatus");
              require(Set.of("DRAFT", "ACTIVE", "PAUSED").contains(status), "模板状态无效");
              Long scope =
                  str(merged, "scopeScheduleId").isEmpty()
                      ? null
                      : id(merged.get("scopeScheduleId"));
              if (scope != null)
                db.one("SELECT schedule_id FROM edu_course_schedule WHERE schedule_id=?", scope);
              var rules =
                  values(
                      "coupon_name",
                      name,
                      "description",
                      description,
                      "discount_amount",
                      discount,
                      "min_spend_amount",
                      min,
                      "scope_schedule_id",
                      scope,
                      "valid_from",
                      from,
                      "valid_until",
                      until,
                      "total_quantity",
                      total,
                      "template_status",
                      status,
                      "update_by",
                      username(actor),
                      "update_time",
                      now());
              long savedId;
              if (template == null) {
                rules.put("template_code", code("CT"));
                rules.put("create_by", username(actor));
                savedId = db.insert("edu_coupon_template", rules);
              } else {
                int issued = integer(old.get("issuedQuantity"), 0, Integer.MAX_VALUE);
                require(total >= issued, "发行上限不得小于已发数量");
                if (issued > 0) {
                  require(
                      name.equals(str(old, "couponName"))
                          && description.equals(str(old, "description"))
                          && discount.compareTo(money(old.get("discountAmount"))) == 0
                          && min.compareTo(money(old.get("minSpendAmount"))) == 0
                          && Objects.equals(
                              scope == null ? "" : scope.toString(), str(old, "scopeScheduleId"))
                          && from.equals(time(old.get("validFrom")))
                          && until.equals(time(old.get("validUntil"))),
                      "发券后规则冻结，仅允许启停或提高发行上限");
                  require(
                      total >= integer(old.get("totalQuantity"), 1, Integer.MAX_VALUE), "发行上限不能降低");
                }
                rules.put("version", integer(old.get("version"), 0, Integer.MAX_VALUE) + 1);
                db.update("edu_coupon_template", "template_id", template, rules);
                savedId = template;
              }
              return db.one("SELECT * FROM edu_coupon_template WHERE template_id=?", savedId);
            });
  }

  public Map<String, Object> issue(long template, long actor, Map<String, Object> b) {
    admin(actor);
    String key = key(b), reason = reason(b, "reason", 255);
    require(b.get("parentIds") instanceof Collection<?>, "请选择家长");
    SortedSet<Long> parents = new TreeSet<>();
    for (Object p : (Collection<?>) b.get("parentIds")) parents.add(id(p));
    require(!parents.isEmpty() && parents.size() <= 100, "每批请选择1至100名家长");
    String hash =
        hash(
            Map.of("templateId", String.valueOf(template), "parentIds", parents, "reason", reason));
    return db.transactions()
        .execute(
            tx -> {
              db.one("SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE", actor);
              var existing =
                  db.rows(
                      "SELECT * FROM edu_user_coupon WHERE granted_by=? AND grant_request_key=?"
                          + " ORDER BY parent_id",
                      actor,
                      key);
              if (!existing.isEmpty()) {
                require(
                    existing.stream().allMatch(r -> hash.equals(r.get("grantRequestHash"))),
                    "IDEMPOTENCY_CONFLICT: 同一批次编号内容不同");
                return Map.of("rows", existing, "total", existing.size());
              }
              var t =
                  db.one(
                      "SELECT * FROM edu_coupon_template WHERE template_id=? FOR UPDATE", template);
              require(
                  "ACTIVE".equals(t.get("templateStatus"))
                      && time(t.get("validUntil")).after(now()),
                  "模板未启用或已过期");
              for (long parent : parents)
                require(
                    db.jdbc()
                            .queryForObject(
                                "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON"
                                    + " u.user_id=ur.user_id JOIN sys_role r ON"
                                    + " r.role_id=ur.role_id WHERE u.user_id=? AND u.status='0' AND"
                                    + " u.del_flag='0' AND r.role_key='parent' AND r.status='0' AND"
                                    + " r.del_flag='0'",
                                Integer.class,
                                parent)
                        > 0,
                    "接收人必须是有效家长账号");
              require(
                  db.jdbc()
                          .update(
                              "UPDATE edu_coupon_template SET"
                                  + " issued_quantity=issued_quantity+?,version=version+1,update_by=?,update_time=?"
                                  + " WHERE template_id=? AND issued_quantity+?<=total_quantity",
                              parents.size(),
                              username(actor),
                              now(),
                              template,
                              parents.size())
                      == 1,
                  "优惠券发行额度不足");
              for (long parent : parents) {
                require(
                    db.rows(
                            "SELECT user_coupon_id FROM edu_user_coupon WHERE template_id=? AND"
                                + " parent_id=?",
                            template,
                            parent)
                        .isEmpty(),
                    "该模板已给所选家长发放过，整批未发放");
                db.insert(
                    "edu_user_coupon",
                    values(
                        "coupon_code",
                        code("UC"),
                        "template_id",
                        template,
                        "parent_id",
                        parent,
                        "coupon_status",
                        "AVAILABLE",
                        "valid_from",
                        time(t.get("validFrom")),
                        "valid_until",
                        time(t.get("validUntil")),
                        "granted_by",
                        actor,
                        "grant_request_key",
                        key,
                        "grant_request_hash",
                        hash,
                        "grant_reason",
                        reason,
                        "create_by",
                        username(actor),
                        "update_by",
                        username(actor)));
              }
              return Map.of(
                  "rows",
                  db.rows(
                      "SELECT * FROM edu_user_coupon WHERE granted_by=? AND grant_request_key=?"
                          + " ORDER BY parent_id",
                      actor,
                      key),
                  "total",
                  parents.size());
            });
  }

  public Map<String, Object> revoke(long coupon, long actor, Map<String, Object> b) {
    admin(actor);
    String reason = reason(b, "reason", 255);
    return db.transactions()
        .execute(
            tx -> {
              var c =
                  db.one("SELECT * FROM edu_user_coupon WHERE user_coupon_id=? FOR UPDATE", coupon);
              require("AVAILABLE".equals(c.get("couponStatus")), "仅可撤回未使用且未锁定的券");
              db.update(
                  "edu_user_coupon",
                  "user_coupon_id",
                  coupon,
                  values(
                      "coupon_status",
                      "REVOKED",
                      "revoked_by",
                      actor,
                      "revoked_time",
                      now(),
                      "revoke_reason",
                      reason,
                      "version",
                      integer(c.get("version"), 0, Integer.MAX_VALUE) + 1,
                      "update_by",
                      username(actor),
                      "update_time",
                      now()));
              return db.one("SELECT * FROM edu_user_coupon WHERE user_coupon_id=?", coupon);
            });
  }

  static final String COUPONS =
      "SELECT"
          + " c.*,t.coupon_name,t.description,t.discount_amount,t.min_spend_amount,t.scope_schedule_id,u.nick_name"
          + " parent_name,CASE WHEN c.coupon_status='AVAILABLE' AND c.valid_until<=NOW(3) THEN"
          + " 'EXPIRED' WHEN c.coupon_status='AVAILABLE' AND c.valid_from>NOW(3) THEN 'NOT_STARTED'"
          + " ELSE c.coupon_status END display_status FROM edu_user_coupon c JOIN"
          + " edu_coupon_template t ON c.template_id=t.template_id JOIN sys_user u ON"
          + " u.user_id=c.parent_id";

  public Map<String, Object> myCoupons(long parent, Map<String, String> q) {
    return grants(parent, false, q);
  }

  public Map<String, Object> grants(long actor, boolean admin, Map<String, String> q) {
    if (admin) admin(actor);
    String where = " WHERE 1=1";
    List<Object> a = new ArrayList<>();
    if (!admin) {
      where += " AND c.parent_id=?";
      a.add(actor);
    }
    if (!str(q, "couponStatus").isEmpty()) {
      where += " AND c.coupon_status=?";
      a.add(str(q, "couponStatus"));
    }
    if (!str(q, "displayStatus").isEmpty()) {
      String d = str(q, "displayStatus");
      if (d.equals("EXPIRED"))
        where += " AND c.coupon_status='AVAILABLE' AND c.valid_until<=NOW(3)";
      else if (d.equals("AVAILABLE"))
        where +=
            " AND c.coupon_status='AVAILABLE' AND c.valid_from<=NOW(3) AND c.valid_until>NOW(3)";
      else {
        where += " AND c.coupon_status=?";
        a.add(d);
      }
    }
    if (!str(q, "parentId").isEmpty() && admin) {
      where += " AND c.parent_id=?";
      a.add(id(q.get("parentId")));
    }
    if (!str(q, "enrollmentId").isEmpty()) {
      var e =
          db.one(
              "SELECT * FROM edu_enrollment WHERE enrollment_id=?"
                  + (admin ? "" : " AND parent_id=?"),
              admin
                  ? new Object[] {id(q.get("enrollmentId"))}
                  : new Object[] {id(q.get("enrollmentId")), actor});
      where +=
          " AND c.parent_id=? AND c.coupon_status='AVAILABLE' AND c.valid_from<=NOW(3) AND"
              + " c.valid_until>NOW(3) AND t.min_spend_amount<=? AND (t.scope_schedule_id IS NULL"
              + " OR t.scope_schedule_id=?)";
      a.add(e.get("parentId"));
      a.add(e.get("originalAmount"));
      a.add(e.get("scheduleId"));
    }
    if (!str(q, "templateId").isBlank()) {
      where += " AND c.template_id=?";
      a.add(id(q.get("templateId")));
    }
    if (!str(q, "parentName").isBlank()) {
      where += " AND u.nick_name LIKE ?";
      a.add("%" + q.get("parentName") + "%");
    }
    return page(
        db,
        COUPONS + where + " ORDER BY c.user_coupon_id DESC",
        "SELECT COUNT(*) FROM edu_user_coupon c JOIN edu_coupon_template t ON"
            + " c.template_id=t.template_id JOIN sys_user u ON u.user_id=c.parent_id"
            + where,
        a,
        q);
  }

  public List<Map<String, Object>> available(Map<String, Object> bill) {
    if (bill.get("originalAmount") == null) return List.of();
    return db.rows(
        COUPONS
            + " WHERE c.parent_id=? AND c.coupon_status='AVAILABLE' AND c.valid_from<=? AND"
            + " c.valid_until>? AND t.min_spend_amount<=? AND (t.scope_schedule_id IS NULL OR"
            + " t.scope_schedule_id=?) ORDER BY c.user_coupon_id",
        bill.get("parentId"),
        now(),
        now(),
        bill.get("originalAmount"),
        bill.get("scheduleId"));
  }

  public BigDecimal discount(long coupon, Map<String, Object> e, boolean lock) {
    var c =
        db.one(
            "SELECT c.*,t.discount_amount,t.min_spend_amount,t.scope_schedule_id FROM"
                + " edu_user_coupon c JOIN edu_coupon_template t ON t.template_id=c.template_id"
                + " WHERE c.user_coupon_id=?"
                + (lock ? " FOR UPDATE" : ""),
            coupon);
    require(
        str(c, "parentId").equals(str(e, "parentId"))
            && "AVAILABLE".equals(c.get("couponStatus"))
            && !time(c.get("validFrom")).after(now())
            && time(c.get("validUntil")).after(now())
            && money(e.get("originalAmount")).compareTo(money(c.get("minSpendAmount"))) >= 0
            && (c.get("scopeScheduleId") == null
                || str(c, "scopeScheduleId").equals(str(e, "scheduleId"))),
        "COUPON_NOT_AVAILABLE: 优惠券不属于当前家长、不可用或不适用本课程");
    return money(c.get("discountAmount")).min(money(e.get("originalAmount")));
  }

  public Map<String, Object> parents(long actor, Map<String, String> q) {
    admin(actor);
    String where =
        " WHERE u.status='0' AND u.del_flag='0' AND EXISTS(SELECT 1 FROM sys_user_role ur JOIN"
            + " sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=u.user_id AND"
            + " r.role_key='parent' AND r.status='0' AND r.del_flag='0')";
    List<Object> a = new ArrayList<>();
    String search = q.getOrDefault("q", q.getOrDefault("search", ""));
    if (!search.isBlank()) {
      where += " AND (u.user_name LIKE ? OR u.nick_name LIKE ?)";
      a.add("%" + search + "%");
      a.add("%" + search + "%");
    }
    return page(
        db,
        "SELECT u.user_id parent_id,u.nick_name parent_name,u.user_name FROM sys_user u"
            + where
            + " ORDER BY u.user_id",
        "SELECT COUNT(*) FROM sys_user u" + where,
        a,
        q);
  }
}

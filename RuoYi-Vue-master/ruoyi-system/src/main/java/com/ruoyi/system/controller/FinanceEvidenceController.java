package com.ruoyi.system.controller;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.finance.FinancePrivateStorage;
import com.ruoyi.system.shop.ShopRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/system/tuition")
public class FinanceEvidenceController {
  private final ShopRepository db;
  private final FinancePrivateStorage storage;

  public FinanceEvidenceController(ShopRepository db, FinancePrivateStorage storage) {
    this.db = db;
    this.storage = storage;
  }

  @PreAuthorize(
      "@ss.hasPermi('system:tuition:collect') or @ss.hasPermi('system:tuitionRefund:execute') or"
          + " @ss.hasPermi('system:tuition:historyVerify')")
  @Log(
      title = "财务私有凭证上传",
      businessType = BusinessType.INSERT,
      isSaveRequestData = false,
      isSaveResponseData = false)
  @PostMapping("/evidence")
  public AjaxResult upload(
      @RequestParam MultipartFile file,
      @RequestParam String businessType,
      @RequestParam long businessId)
      throws Exception {
    String permission =
        switch (businessType) {
          case "PAYMENT" -> "system:tuition:collect";
          case "REFUND" -> "system:tuitionRefund:execute";
          case "HISTORY" -> "system:tuition:historyVerify";
          default -> throw new ServiceException("凭证业务无效");
        };
    if (!SecurityUtils.hasPermi(permission)) throw new ServiceException("无权上传此类业务凭证");
    if ("REFUND".equals(businessType))
      db.one(
          "SELECT refund_id FROM edu_tuition_refund WHERE refund_id=? AND refund_status IN"
              + " ('APPROVED','PROCESSING','UNKNOWN')",
          businessId);
    else
      db.one(
          "SELECT enrollment_id FROM edu_enrollment WHERE enrollment_id=?"
              + ("HISTORY".equals(businessType)
                  ? " AND billing_status='HISTORY_PENDING'"
                  : " AND billing_status='OPEN'"),
          businessId);
    return AjaxResult.success(
        storage.uploadEvidence(SecurityUtils.getUserId(), businessType, businessId, file));
  }

  @PreAuthorize("@ss.hasPermi('system:tuition:query')")
  @GetMapping("/payments/{id}/evidence")
  public void payment(@PathVariable long id, HttpServletResponse r) throws Exception {
    send(
        db.one("SELECT evidence_key FROM edu_tuition_payment WHERE payment_id=?", id)
            .get("evidenceKey"),
        r);
  }

  @PreAuthorize("@ss.hasPermi('system:tuitionRefund:query')")
  @GetMapping("/refunds/{id}/evidence")
  public void refund(@PathVariable long id, HttpServletResponse r) throws Exception {
    send(
        db.one("SELECT evidence_key FROM edu_tuition_refund WHERE refund_id=?", id)
            .get("evidenceKey"),
        r);
  }

  @PreAuthorize("@ss.hasPermi('system:tuition:historyVerify')")
  @GetMapping("/{id}/history-evidence")
  public void history(@PathVariable long id, HttpServletResponse r) throws Exception {
    Object snapshot =
        db.one("SELECT history_verification_snapshot FROM edu_enrollment WHERE enrollment_id=?", id)
            .get("historyVerificationSnapshot");
    if (snapshot == null) throw new ServiceException("该历史报名还没有核验凭证");
    send(JSON.parseObject(snapshot.toString()).get("evidenceKey"), r);
  }

  private void send(Object key, HttpServletResponse response) throws Exception {
    if (key == null || key.toString().isBlank()) throw new ServiceException("未上传此业务的凭证");
    var path = storage.resolveEvidence(key.toString());
    String name = path.getFileName().toString();
    TuitionReceiptController.send(
        path, "evidence." + name.substring(name.lastIndexOf('.') + 1), response);
  }
}

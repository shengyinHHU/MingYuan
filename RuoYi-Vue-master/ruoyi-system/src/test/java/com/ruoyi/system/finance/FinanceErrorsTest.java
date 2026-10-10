package com.ruoyi.system.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.ruoyi.system.controller.FinanceExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class FinanceErrorsTest {
  @Test
  void financialDatabaseConflictNeverReturnsSqlOrPrivateValues() {
    var response =
        new FinanceExceptionHandler()
            .database(
                new DataIntegrityViolationException(
                    "INSERT INTO edu_tuition_payment payer_name='private-person',"
                        + " evidence_key='/private/file'"));
    String message = response.get("msg").toString();
    assertFalse(message.contains("INSERT"));
    assertFalse(message.contains("private-person"));
    assertFalse(message.contains("/private"));
    assertEquals(500, response.get("code"));
  }
}

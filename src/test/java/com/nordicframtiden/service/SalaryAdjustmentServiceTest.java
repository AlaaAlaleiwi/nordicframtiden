package com.nordicframtiden.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.nordicframtiden.service.model.SalaryAdjustment;
import com.nordicframtiden.service.model.SalaryAdjustmentRepository;

class SalaryAdjustmentServiceTest {
  private final SalaryAdjustmentService service =
      new SalaryAdjustmentService(mock(SalaryAdjustmentRepository.class));

  @Test
  void mileageAboveSkatteverketLimitMakesOnlyTheAllowedPartTaxFree() {
    SalaryAdjustment adjustment = reimbursement(
        SalaryAdjustment.ReimbursementType.MILEAGE_OWN_CAR, "400.00", "10.00");
    assertEquals(new BigDecimal("250.00"), service.taxFreePortion(adjustment));
  }

  @Test
  void documentedCompanyExpenseIsTaxFreeWhenConfirmed() {
    SalaryAdjustment adjustment = reimbursement(
        SalaryAdjustment.ReimbursementType.DOCUMENTED_EXPENSE, "875.50", null);
    adjustment.setReceiptReference("receipt-123");
    assertEquals(new BigDecimal("875.50"), service.taxFreePortion(adjustment));
  }

  private SalaryAdjustment reimbursement(SalaryAdjustment.ReimbursementType type,
      String amount, String quantity) {
    SalaryAdjustment adjustment = new SalaryAdjustment();
    adjustment.setTaxTreatment(SalaryAdjustment.TaxTreatment.TAX_FREE);
    adjustment.setReimbursementType(type);
    adjustment.setAmount(new BigDecimal(amount));
    adjustment.setQuantity(quantity == null ? null : new BigDecimal(quantity));
    adjustment.setTaxFreeEligibilityConfirmed(true);
    return adjustment;
  }
}

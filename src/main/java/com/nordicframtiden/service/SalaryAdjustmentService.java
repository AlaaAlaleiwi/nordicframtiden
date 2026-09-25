package com.nordicframtiden.service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nordicframtiden.service.model.SalaryAdjustment;
import com.nordicframtiden.service.model.SalaryAdjustmentRepository;

@Service
public class SalaryAdjustmentService {
  private final SalaryAdjustmentRepository repository;
  public SalaryAdjustmentService(SalaryAdjustmentRepository repository){this.repository=repository;}
  public List<SalaryAdjustment> forMonth(Long userId,int year,int month){return repository.findByUserIdAndYearAndMonthOrderById(userId,year,month);}
  public BigDecimal annualOneTimeTotal(Long userId,int year){return repository.findByUserIdAndYear(userId,year).stream().filter(a->a.getTaxTreatment()==SalaryAdjustment.TaxTreatment.ONE_TIME_TAXABLE).map(SalaryAdjustment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
  @Transactional public List<SalaryAdjustment> replace(Long userId,int year,int month,List<AdjustmentInput> inputs){
    if(month<1||month>12) throw new IllegalArgumentException("Invalid salary month");
    repository.deleteByUserIdAndYearAndMonth(userId,year,month);
    return repository.saveAll(inputs.stream().map(i->{
      if(i.name()==null||i.name().isBlank()||i.amount()==null||i.amount().signum()<0||i.taxTreatment()==null) throw new IllegalArgumentException("Invalid salary adjustment");
      if(i.taxTreatment()==SalaryAdjustment.TaxTreatment.TAX_FREE && (i.reimbursementType()==null || !i.taxFreeEligibilityConfirmed())) throw new IllegalArgumentException("Tax-free reimbursement type and eligibility confirmation are required");
      if(i.taxTreatment()==SalaryAdjustment.TaxTreatment.TAX_FREE && i.reimbursementType()!=SalaryAdjustment.ReimbursementType.DOCUMENTED_EXPENSE && (i.quantity()==null || i.quantity().signum()<=0)) throw new IllegalArgumentException("A positive reimbursement quantity is required");
      if(i.reimbursementType()==SalaryAdjustment.ReimbursementType.DOCUMENTED_EXPENSE && (i.receiptReference()==null || i.receiptReference().isBlank())) throw new IllegalArgumentException("Receipt reference is required for a documented expense");
      var a=new SalaryAdjustment(); a.setUserId(userId);a.setYear(year);a.setMonth(month);a.setName(i.name().trim());a.setAmount(i.amount().setScale(2,RoundingMode.HALF_UP));a.setTaxTreatment(i.taxTreatment());a.setReimbursementType(i.reimbursementType());a.setQuantity(i.quantity());a.setReceiptReference(i.receiptReference()==null?null:i.receiptReference().trim());a.setTaxFreeEligibilityConfirmed(i.taxFreeEligibilityConfirmed());return a;
    }).toList());
  }
  public BigDecimal taxFreePortion(SalaryAdjustment adjustment) {
    if (adjustment.getTaxTreatment()!=SalaryAdjustment.TaxTreatment.TAX_FREE || !adjustment.isTaxFreeEligibilityConfirmed() || adjustment.getReimbursementType()==null) return BigDecimal.ZERO;
    if (adjustment.getReimbursementType()==SalaryAdjustment.ReimbursementType.DOCUMENTED_EXPENSE) return adjustment.getAmount();
    BigDecimal rate = switch (adjustment.getReimbursementType()) {
      case MILEAGE_OWN_CAR -> new BigDecimal("25.00");
      case MILEAGE_COMPANY_ELECTRIC -> new BigDecimal("9.50");
      case MILEAGE_COMPANY_OTHER -> new BigDecimal("12.00");
      case DOMESTIC_TRAVEL_FULL_DAY -> new BigDecimal("300.00");
      case DOMESTIC_TRAVEL_HALF_DAY, DOMESTIC_TRAVEL_NIGHT -> new BigDecimal("150.00");
      case DOCUMENTED_EXPENSE -> BigDecimal.ZERO;
    };
    return adjustment.getAmount().min(adjustment.getQuantity().multiply(rate)).setScale(2,RoundingMode.HALF_UP);
  }
  public record AdjustmentInput(String name,BigDecimal amount,SalaryAdjustment.TaxTreatment taxTreatment,
      SalaryAdjustment.ReimbursementType reimbursementType,BigDecimal quantity,String receiptReference,
      boolean taxFreeEligibilityConfirmed){}
}

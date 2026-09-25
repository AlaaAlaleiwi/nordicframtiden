package com.nordicframtiden.service;
import java.math.BigDecimal;
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
      var a=new SalaryAdjustment(); a.setUserId(userId);a.setYear(year);a.setMonth(month);a.setName(i.name().trim());a.setAmount(i.amount().setScale(2));a.setTaxTreatment(i.taxTreatment());return a;
    }).toList());
  }
  public record AdjustmentInput(String name,BigDecimal amount,SalaryAdjustment.TaxTreatment taxTreatment){}
}

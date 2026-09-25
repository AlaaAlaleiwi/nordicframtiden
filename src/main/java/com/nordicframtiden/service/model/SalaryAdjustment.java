package com.nordicframtiden.service.model;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "salary_adjustment")
public class SalaryAdjustment {
  public enum TaxTreatment { REGULAR_TAXABLE, ONE_TIME_TAXABLE, TAX_FREE }
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name="user_id", nullable=false) private Long userId;
  @Column(name="salary_year", nullable=false) private int year;
  @Column(name="salary_month", nullable=false) private int month;
  @Column(nullable=false, length=120) private String name;
  @Column(nullable=false, precision=12, scale=2) private BigDecimal amount;
  @Enumerated(EnumType.STRING) @Column(name="tax_treatment", nullable=false) private TaxTreatment taxTreatment;
  public Long getId(){return id;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
  public int getYear(){return year;} public void setYear(int v){year=v;} public int getMonth(){return month;} public void setMonth(int v){month=v;}
  public String getName(){return name;} public void setName(String v){name=v;} public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
  public TaxTreatment getTaxTreatment(){return taxTreatment;} public void setTaxTreatment(TaxTreatment v){taxTreatment=v;}
}

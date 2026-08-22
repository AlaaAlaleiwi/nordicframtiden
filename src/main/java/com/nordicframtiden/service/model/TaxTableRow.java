package com.nordicframtiden.service.model;

import java.io.Serial;

import jakarta.persistence.*;

@Entity
@Table(name = "tax_table_row")
public class TaxTableRow {

  @Serial
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tax_year", nullable = false)
  private Integer taxYear;

  @Column(name = "table_number", nullable = false)
  private Integer tableNumber;

  @Column(name = "income_from", nullable = false)
  private Integer incomeFrom;

  @Column(name = "income_to", nullable = false)
  private Integer incomeTo;

  @Column(name = "col_1", nullable = false)
  private Integer col1;
  @Column(name = "col_2", nullable = false)
  private Integer col2;
  @Column(name = "col_3", nullable = false)
  private Integer col3;
  @Column(name = "col_4", nullable = false)
  private Integer col4;
  @Column(name = "col_5", nullable = false)
  private Integer col5;
  @Column(name = "col_6", nullable = false)
  private Integer col6;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Integer getTaxYear() {
    return taxYear;
  }

  public void setTaxYear(Integer taxYear) {
    this.taxYear = taxYear;
  }

  public Integer getTableNumber() {
    return tableNumber;
  }

  public void setTableNumber(Integer tableNumber) {
    this.tableNumber = tableNumber;
  }

  public Integer getIncomeFrom() {
    return incomeFrom;
  }

  public void setIncomeFrom(Integer incomeFrom) {
    this.incomeFrom = incomeFrom;
  }

  public Integer getIncomeTo() {
    return incomeTo;
  }

  public void setIncomeTo(Integer incomeTo) {
    this.incomeTo = incomeTo;
  }

  public Integer getCol1() {
    return col1;
  }

  public void setCol1(Integer col1) {
    this.col1 = col1;
  }

  public Integer getCol2() {
    return col2;
  }

  public void setCol2(Integer col2) {
    this.col2 = col2;
  }

  public Integer getCol3() {
    return col3;
  }

  public void setCol3(Integer col3) {
    this.col3 = col3;
  }

  public Integer getCol4() {
    return col4;
  }

  public void setCol4(Integer col4) {
    this.col4 = col4;
  }

  public Integer getCol5() {
    return col5;
  }

  public void setCol5(Integer col5) {
    this.col5 = col5;
  }

  public Integer getCol6() {
    return col6;
  }

  public void setCol6(Integer col6) {
    this.col6 = col6;
  }

}
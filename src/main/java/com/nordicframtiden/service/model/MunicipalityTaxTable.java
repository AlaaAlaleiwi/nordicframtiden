package com.nordicframtiden.service.model;

import jakarta.persistence.*;

@Entity
@Table(name = "municipality_tax_table")
@IdClass(MunicipalityTaxTableId.class)
public class MunicipalityTaxTable {

  @Id
  @Column(name = "municipality_code", length = 4, nullable = false)
  private String municipalityCode;

  @Id
  @Column(name = "tax_year", nullable = false)
  private int taxYear;

  @Column(name = "table_number", nullable = false)
  private int tableNumber;

  public String getMunicipalityCode() {
    return municipalityCode;
  }

  public void setMunicipalityCode(String municipalityCode) {
    this.municipalityCode = municipalityCode;
  }

  public int getTaxYear() {
    return taxYear;
  }

  public void setTaxYear(int taxYear) {
    this.taxYear = taxYear;
  }

  public int getTableNumber() {
    return tableNumber;
  }

  public void setTableNumber(int tableNumber) {
    this.tableNumber = tableNumber;
  }
}

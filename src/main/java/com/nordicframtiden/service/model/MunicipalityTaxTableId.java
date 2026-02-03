package com.nordicframtiden.service.model;

import java.io.Serializable;
import java.util.Objects;

public class MunicipalityTaxTableId implements Serializable {

  private String municipalityCode;
  private int taxYear;

  public MunicipalityTaxTableId() {}

  public MunicipalityTaxTableId(String municipalityCode, int taxYear) {
    this.municipalityCode = municipalityCode;
    this.taxYear = taxYear;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MunicipalityTaxTableId that)) return false;
    return taxYear == that.taxYear &&
        Objects.equals(municipalityCode, that.municipalityCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(municipalityCode, taxYear);
  }
}
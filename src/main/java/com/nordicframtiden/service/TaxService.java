package com.nordicframtiden.service;

import org.springframework.stereotype.Service;

import com.nordicframtiden.service.model.MunicipalityTaxTableRepository;
import com.nordicframtiden.service.model.TaxTableRow;
import com.nordicframtiden.service.model.TaxTableRowRepository;

@Service
public class TaxService {
  private final TaxTableRowRepository taxRepo;
  private final MunicipalityTaxTableRepository muniRepo;

  public TaxService(TaxTableRowRepository taxRepo, MunicipalityTaxTableRepository muniRepo) {
    this.taxRepo = taxRepo;
    this.muniRepo = muniRepo;
}

  public int resolveTaxColumn(int yearOfBirth, int taxYear) {
    int age = taxYear - yearOfBirth;
    return (age >= 66) ? 2 : 1; // adjust later if needed
  }

  public int resolveTableNumber(String municipalityCode, int taxYear) {
    return muniRepo.findByMunicipalityCodeAndTaxYear(municipalityCode, taxYear)
        .orElseThrow(() -> new IllegalArgumentException("No tax table mapping for municipality " + municipalityCode))
        .getTableNumber();
  }

  public int lookupPreliminaryTax(int taxYear, int tableNumber, int taxColumn, int grossSalaryInt) {
    TaxTableRow row = taxRepo.findRow(taxYear, tableNumber, grossSalaryInt)
        .orElseThrow(() -> new IllegalArgumentException("No tax row for salary " + grossSalaryInt));

    return switch (taxColumn) {
      case 1 -> row.getCol1();
      case 2 -> row.getCol2();
      case 3 -> row.getCol3();
      case 4 -> row.getCol4();
      case 5 -> row.getCol5();
      case 6 -> row.getCol6();
      default -> throw new IllegalArgumentException("Invalid tax column " + taxColumn);
    };
  }
}

package com.nordicframtiden.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nordicframtiden.service.model.MunicipalityTaxTableRepository;
import com.nordicframtiden.service.model.TaxTableRow;
import com.nordicframtiden.service.model.TaxTableRowRepository;

@Service
public class TaxService {
  private static final Logger log = LoggerFactory.getLogger(TaxService.class);

  private final TaxTableRowRepository taxRepo;
  private final MunicipalityTaxTableRepository muniRepo;
  private final SkatteverketTaxClient skatteverketTaxClient;

  public TaxService(TaxTableRowRepository taxRepo, MunicipalityTaxTableRepository muniRepo,
      SkatteverketTaxClient skatteverketTaxClient) {
    this.taxRepo = taxRepo;
    this.muniRepo = muniRepo;
    this.skatteverketTaxClient = skatteverketTaxClient;
  }

  public int resolveTaxColumn(int yearOfBirth, int taxYear) {
    // With only a birth year available, taxYear - 67 is the latest cohort
    // guaranteed to have turned 66 before the start of the tax year.
    return yearOfBirth <= taxYear - 67 ? 3 : 1;
  }

  public int resolveTableNumber(String municipalityCode, int taxYear) {
    return muniRepo.findByMunicipalityCodeAndTaxYear(municipalityCode, taxYear)
        .orElseThrow(() -> new IllegalArgumentException("No tax table mapping for municipality " + municipalityCode))
        .getTableNumber();
  }

  public int lookupPreliminaryTax(int taxYear, int tableNumber, int taxColumn, int grossSalaryInt) {
    try {
      var externalTax = skatteverketTaxClient.lookupPreliminaryTax(
          taxYear, tableNumber, taxColumn, grossSalaryInt);
      if (externalTax.isPresent()) {
        return externalTax.getAsInt();
      }
    } catch (SkatteverketTaxClient.SkatteverketUnavailableException exception) {
      log.warn("Skatteverket tax API unavailable; using local tax table fallback: {}",
          exception.getMessage());
    }

    TaxTableRow row = taxRepo.findRow(taxYear, tableNumber, grossSalaryInt)
        .orElseThrow(() -> new IllegalArgumentException("No tax row for salary " + grossSalaryInt));

    int tableValue = switch (taxColumn) {
      case 1 -> row.getCol1();
      case 2 -> row.getCol2();
      case 3 -> row.getCol3();
      case 4 -> row.getCol4();
      case 5 -> row.getCol5();
      case 6 -> row.getCol6();
      default -> throw new IllegalArgumentException("Invalid tax column " + taxColumn);
    };
    return Boolean.TRUE.equals(row.getPercentage())
        ? (int) Math.round(grossSalaryInt * tableValue / 100.0)
        : tableValue;
  }
}

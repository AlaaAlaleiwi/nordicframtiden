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
    if (Boolean.TRUE.equals(row.getPercentage())) {
      // Percentage tables: the stored value is a percentage of the gross salary
      // and the withholding is rounded down to whole kronor.
      return (int) ((long) grossSalaryInt * tableValue / 100);
    }
    if (row.getIncomeFrom() != null
        && row.getIncomeFrom() > TaxTableRow.MONTHLY_KRONOR_INCOME_LIMIT) {
      // Kronor rows never exist above 80 000 kr per month. A high-income row
      // without the percentage flag carries a raw percentage (e.g. 45) that
      // would otherwise be paid out as 45 kronor instead of ~45 percent.
      throw new IllegalStateException("Corrupt tax table row for year " + row.getTaxYear()
          + " table " + row.getTableNumber() + " income " + row.getIncomeFrom()
          + ": high-income rows must be percentage rows");
    }
    return tableValue;
  }
}

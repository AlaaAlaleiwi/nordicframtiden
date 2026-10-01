package com.nordicframtiden.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nordicframtiden.service.model.MunicipalityTaxTableRepository;
import com.nordicframtiden.service.model.TaxTableRow;
import com.nordicframtiden.service.model.TaxTableRowRepository;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class TaxServiceTest {
  private final TaxTableRowRepository taxRepo = mock(TaxTableRowRepository.class);
  private final MunicipalityTaxTableRepository municipalityRepo =
      mock(MunicipalityTaxTableRepository.class);
  private final SkatteverketTaxClient skatteverketClient = mock(SkatteverketTaxClient.class);
  private final TaxService taxService =
      new TaxService(taxRepo, municipalityRepo, skatteverketClient);

  @Test
  void usesSkatteverketResultWhenIntegrationIsEnabled() {
    when(skatteverketClient.lookupPreliminaryTax(2026, 34, 1, 35_000))
        .thenReturn(OptionalInt.of(6_252));

    assertEquals(6_252, taxService.lookupPreliminaryTax(2026, 34, 1, 35_000));
    verify(taxRepo, never()).findRow(2026, 34, 35_000);
  }

  @Test
  void usesDatabaseWhenIntegrationIsDisabled() {
    when(skatteverketClient.lookupPreliminaryTax(2026, 34, 1, 35_000))
        .thenReturn(OptionalInt.empty());
    when(taxRepo.findRow(2026, 34, 35_000)).thenReturn(Optional.of(rowWithColumnOne(6_252)));

    assertEquals(6_252, taxService.lookupPreliminaryTax(2026, 34, 1, 35_000));
  }

  @Test
  void usesDatabaseWhenSkatteverketIsTemporarilyUnavailable() {
    when(skatteverketClient.lookupPreliminaryTax(2026, 34, 1, 35_000))
        .thenThrow(new SkatteverketTaxClient.SkatteverketUnavailableException("maintenance"));
    when(taxRepo.findRow(2026, 34, 35_000)).thenReturn(Optional.of(rowWithColumnOne(6_252)));

    assertEquals(6_252, taxService.lookupPreliminaryTax(2026, 34, 1, 35_000));
  }

  @Test
  void salaryUsesColumnOneOrThreeNeverPensionColumnTwo() {
    assertEquals(1, taxService.resolveTaxColumn(1960, 2026));
    assertEquals(3, taxService.resolveTaxColumn(1959, 2026));
  }

  private TaxTableRow rowWithColumnOne(int tax) {
    TaxTableRow row = new TaxTableRow();
    row.setCol1(tax);
    return row;
  }

  @Test
  void calculatesPercentageRowsForHighSalaries() {
    TaxTableRow row = rowWithColumnOne(40);
    row.setPercentage(true);
    when(taxRepo.findRow(2026, 32, 100_000)).thenReturn(Optional.of(row));

    assertEquals(40_000, taxService.lookupPreliminaryTax(2026, 32, 1, 100_000));
  }

  @Test
  void percentageRowsRoundDownToWholeKronor() {
    // Official 2026 table 34: 100 002 kr at 38 percent withholds 38 000 kr.
    TaxTableRow row = rowWithColumnOne(38);
    row.setPercentage(true);
    when(taxRepo.findRow(2026, 34, 100_002)).thenReturn(Optional.of(row));

    assertEquals(38_000, taxService.lookupPreliminaryTax(2026, 34, 1, 100_002));
  }

  @Test
  void reproducesSaraOctober2026DraftWithholding() {
    // Production incident 2026-10: 233 600 kr gross returned 45 kr because the
    // row was a percentage row (47 %) stored without the percentage flag.
    // The official table 34 row 225 001-240 800 is 47 % in column 1.
    TaxTableRow corruptRow = rowWithColumnOne(47);
    corruptRow.setIncomeFrom(225_001);
    corruptRow.setIncomeTo(240_800);
    when(taxRepo.findRow(2026, 34, 233_600)).thenReturn(Optional.of(corruptRow));

    var exception = assertThrows(IllegalStateException.class,
        () -> taxService.lookupPreliminaryTax(2026, 34, 1, 233_600));
    assertTrue(exception.getMessage().contains("percentage rows"));

    // With the percentage flag restored the withholding is correct.
    corruptRow.setPercentage(true);
    assertEquals(109_792, taxService.lookupPreliminaryTax(2026, 34, 1, 233_600));
  }

  @Test
  void kronorRowsInsideTheMonthlyLimitStillUseTheStoredAmount() {
    TaxTableRow row = rowWithColumnOne(2_990);
    row.setIncomeFrom(17_901);
    row.setIncomeTo(18_000);
    when(taxRepo.findRow(2026, 34, 18_000)).thenReturn(Optional.of(row));

    assertEquals(2_990, taxService.lookupPreliminaryTax(2026, 34, 1, 18_000));
  }
}

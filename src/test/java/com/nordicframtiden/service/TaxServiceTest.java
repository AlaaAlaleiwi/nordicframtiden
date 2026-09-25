package com.nordicframtiden.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

package com.nordicframtiden.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class SkatteverketTaxTableImporterTest {
  @Test
  void discoversMonthlyTextFileInsideRequestedYearSection() {
    String html = """
        <h2>Samtliga tabeller 2027 för månadslön</h2>
        <a href="/download/2027/allmanna-tabeller-manad.txt">Månadslön txt</a>
        <h2>Samtliga tabeller 2026 för månadslön</h2>
        <a href="/download/2026/allmanna-tabeller-manad.txt">Månadslön txt</a>
        """;

    assertEquals(URI.create("https://www.skatteverket.se/download/2027/allmanna-tabeller-manad.txt"),
        SkatteverketTaxTableImporter.discoverMonthlyTableUri(2027, html));
  }

  @Test
  void parsesFixedWidthAmountAndPercentageRows() {
    String file = """
        30B29      1   2000    0    0    0    0    0    0
        30%421327801          61   61   61   51   61   61
        """;

    var rows = SkatteverketTaxTableImporter.parseMonthlyTable(2026, file);

    assertEquals(2, rows.size());
    assertEquals(29, rows.get(0).tableNumber());
    assertEquals(1, rows.get(0).incomeFrom());
    assertEquals(2000, rows.get(0).incomeTo());
    assertFalse(rows.get(0).percentage());
    assertEquals(42, rows.get(1).tableNumber());
    assertEquals(1327801, rows.get(1).incomeFrom());
    assertEquals(Integer.MAX_VALUE, rows.get(1).incomeTo());
    assertTrue(rows.get(1).percentage());
  }

  @Test
  void parsesConcatenatedFiveCharacterTaxColumnsWithoutWhitespace() {
    // Official 2026 table 34 row: incomes of 10 000 kr and above fill the
    // five-character fields completely, so the six columns run together.
    var rows = SkatteverketTaxTableImporter.parseMonthlyTable(2026,
        "30B34  36201  36400 6593 6445 3340 65931003610036\n");

    assertEquals(1, rows.size());
    assertFalse(rows.get(0).percentage());
    assertEquals(34, rows.get(0).tableNumber());
    assertEquals(36_201, rows.get(0).incomeFrom());
    assertEquals(36_400, rows.get(0).incomeTo());
    assertEquals(6_593, rows.get(0).col1());
    assertEquals(6_445, rows.get(0).col2());
    assertEquals(3_340, rows.get(0).col3());
    assertEquals(6_593, rows.get(0).col4());
    assertEquals(10_036, rows.get(0).col5());
    assertEquals(10_036, rows.get(0).col6());
  }

  @Test
  void parsesEveryOfficial2026MonthlyRow() throws Exception {
    // Copy the real official file when it is available (audit workspace) so the
    // production parser is exercised against actual Skatteverket data.
    Path official = Path.of("../audit/tax-2026/monthly-2026.txt");
    if (!Files.isReadable(official)) {
      return; // file not checked out; the unit tests above cover the format
    }
    String text = Files.readString(official, StandardCharsets.UTF_8);

    List<SkatteverketTaxTableImporter.TaxTableImportRow> rows =
        SkatteverketTaxTableImporter.parseMonthlyTable(2026, text);

    assertEquals(7_966, rows.size());
    SkatteverketTaxTableImporter.validate(rows);

    var row18k = rows.stream().filter(r -> r.tableNumber() == 34 && r.incomeFrom() <= 18_000
        && r.incomeTo() >= 18_000).findFirst().orElseThrow();
    assertEquals(2_990, row18k.col1());
    var row36201 = rows.stream().filter(r -> r.tableNumber() == 34 && r.incomeFrom() == 36_201)
        .findFirst().orElseThrow();
    assertEquals(7_709, row36201.col1());
    assertEquals(11_771, row36201.col5());
    var row80k = rows.stream().filter(r -> r.tableNumber() == 34 && r.incomeFrom() <= 80_000
        && r.incomeTo() >= 80_000).findFirst().orElseThrow();
    assertEquals(27_246, row80k.col1());
    var row80_001 = rows.stream().filter(r -> r.tableNumber() == 34 && r.incomeFrom() == 80_001)
        .findFirst().orElseThrow();
    assertTrue(row80_001.percentage());
  }

  @Test
  void validationRejectsKronorRowsAboveTheMonthlyKronorLimit() {
    var rows = List.of(
        new SkatteverketTaxTableImporter.TaxTableImportRow(2026, 34, 225_001, 240_800,
            47, 47, 46, 40, 49, 49, false));

    var exception = assertThrows(IllegalArgumentException.class,
        () -> SkatteverketTaxTableImporter.validate(rows));
    assertTrue(exception.getMessage().contains("Kronor row above 80000 kr"),
        "unexpected message: " + exception.getMessage());
  }

  @Test
  void validationRejectsTablesWithoutOpenEndedPercentageRow() {
    // The table stops at 240 800 kr without an open-ended top row.
    var rows = List.of(
        new SkatteverketTaxTableImporter.TaxTableImportRow(2026, 34, 1, 2_000,
            0, 0, 0, 0, 0, 0, false),
        new SkatteverketTaxTableImporter.TaxTableImportRow(2026, 34, 2_001, 240_800,
            47, 47, 46, 40, 49, 49, true));

    var exception = assertThrows(IllegalArgumentException.class,
        () -> SkatteverketTaxTableImporter.validate(rows));
    assertTrue(exception.getMessage().contains("open-ended percentage row"),
        "unexpected message: " + exception.getMessage());
  }

  @Test
  void parsesOneTimeTablesIncludingOpenEndedBracket() {
    StringBuilder html = new StringBuilder();
    for (int column = 1; column <= 6; column++) {
      html.append("<div id=\"Engangstabellkolumn").append(column).append("2027\"><table><tbody>")
          .append("<tr><td><p>0</p></td><td>–</td><td><p>25 000</p></td><td><p>0 %</p></td></tr>")
          .append("<tr><td><p>25 001</p></td><td>–</td><td><br></td><td><p>50 %</p></td></tr>")
          .append("</tbody></table></div>");
    }
    var rows = SkatteverketTaxTableImporter.parseOneTimeTables(2027, html.toString());
    assertEquals(12, rows.size());
    assertEquals(25_001, rows.get(1).annualIncomeFrom());
    assertEquals(Integer.MAX_VALUE, rows.get(1).annualIncomeTo());
    assertEquals(50, rows.get(1).taxPercent());
  }
}

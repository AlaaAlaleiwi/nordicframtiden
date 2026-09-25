package com.nordicframtiden.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

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

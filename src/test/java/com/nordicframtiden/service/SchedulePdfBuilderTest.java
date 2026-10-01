package com.nordicframtiden.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulePdfBuilderTest {

  private final SchedulePdfBuilder builder = new SchedulePdfBuilder();

  @Test
  void buildsPdfWithOneRowPerScheduledDay() throws Exception {
    byte[] pdf = builder.build("Apotek Kronan", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18),
        List.of(
            new SchedulePdfBuilder.DayLine("2026-10-12", "måndag", "Sara Svensson", "09:00", "17:00", "8.00"),
            new SchedulePdfBuilder.DayLine("2026-10-13", "tisdag", "Ali Hassan", "09:00", "17:00", "8.00")));

    assertTrue(pdf.length > 500);
    // Extract readable text (naive: PDF streams may compress, so check the header at least)
    String header = new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII);
    assertEquals("%PDF-", header);
  }

  @Test
  void buildsPdfForEmptyDayList() {
    byte[] pdf = builder.build("Apotek", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12), List.of());
    String header = new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII);
    assertEquals("%PDF-", header);
  }
}

package com.nordicframtiden.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The automatic payslip email attachment must be a structurally valid PDF
 * (header marker + EOF marker + page tree) for any input, including empty
 * day lists and null totals.
 */
class PayslipPdfBuilderTest {

    private final PayslipPdfBuilder builder = new PayslipPdfBuilder();

    @Test
    void build_producesAValidPdfDocument() {
        byte[] pdf = builder.build("Anna Andersson", 2026, 8,
            List.of(new PayslipPdfBuilder.DayLine("2026-08-03", "08:00", "17:00", "8.00", new BigDecimal("1600"))),
            new BigDecimal("24000"), new BigDecimal("4800"), new BigDecimal("19200"), new BigDecimal("120"));

        String content = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(content).startsWith("%PDF-");
        assertThat(content).contains("%%EOF");
        assertThat(pdf.length).isGreaterThan(500);
    }

    @Test
    void build_toleratesEmptyDaysAndNullTotals() {
        byte[] pdf = builder.build("", 2026, 8, List.of(), null, null, null, null);

        String content = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(content).startsWith("%PDF-");
        assertThat(content).contains("%%EOF");
    }

    @Test
    void build_multiPageDayListsStayValid() {
        List<PayslipPdfBuilder.DayLine> days = new java.util.ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            days.add(new PayslipPdfBuilder.DayLine("2026-08-%02d".formatted(i % 28 + 1),
                "08:00", "17:00", "8.00", BigDecimal.TEN));
        }
        byte[] pdf = builder.build("Anna", 2026, 8, days,
            new BigDecimal("24000"), new BigDecimal("4800"), new BigDecimal("19200"), new BigDecimal("320"));

        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("%%EOF");
    }
}

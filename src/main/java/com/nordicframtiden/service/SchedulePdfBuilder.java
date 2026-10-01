package com.nordicframtiden.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders the schedule PDF attached to the confirmation email of the
 * admin schedule wizard. Pure function of its inputs (mirrors
 * {@link PayslipPdfBuilder}) so it is unit-testable without Spring.
 * OpenPDF is already on the classpath; no new dependency.
 */
@Component
public class SchedulePdfBuilder {

    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 10);
    private static final Font SMALL = new Font(Font.HELVETICA, 8);
    private static final Font TABLE_HEADER = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Color HEADER_BG = new Color(15, 81, 50);
    private static final Color ROW_BG = new Color(244, 246, 248);

    /** One rendered row: a scheduled day for one employee. */
    public record DayLine(String date, String weekday, String employee, String from, String to, String hours) {
    }

    public byte[] build(String pharmacyName, LocalDate start, LocalDate end, List<DayLine> days) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Schema", TITLE));
            document.add(spacer(8));
            document.add(new Paragraph("Apotek: " + safe(pharmacyName), BODY));
            document.add(new Paragraph("Period: " + periodLabel(start, end), BODY));
            document.add(spacer(10));

            document.add(new Paragraph("Bokade dagar", HEADING));
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setSpacingBefore(6);
            table.setWidths(new float[]{2f, 1.8f, 2.6f, 1.3f, 1.3f, 1.2f});
            headerRow(table, "Datum", "Veckodag", "Anställd", "Från", "Till", "Timmar");
            if (days.isEmpty()) {
                cells(table, "-", "-", "-", "-", "-", "-");
            }
            for (DayLine day : days) {
                cells(table, day.date(), day.weekday(), day.employee(), day.from(), day.to(), day.hours());
            }
            document.add(table);

            document.add(spacer(14));
            document.add(new Paragraph(
                "Automatiskt utskick från Nordic Framtiden — genererad "
                    + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                SMALL));
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build schedule PDF", e);
        }
    }

    private String periodLabel(LocalDate start, LocalDate end) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        if (start.equals(end)) {
            return start.format(fmt);
        }
        return start.format(fmt) + " – " + end.format(fmt);
    }

    private void headerRow(PdfPTable table, String... labels) {
        for (String label : labels) {
            var cell = new PdfPCell(new com.lowagie.text.Phrase(label, TABLE_HEADER));
            cell.setBackgroundColor(HEADER_BG);
            cell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void cells(PdfPTable table, String... values) {
        for (String value : values) {
            var cell = new PdfPCell(new com.lowagie.text.Phrase(safe(value), BODY));
            cell.setBorder(Rectangle.BOTTOM);
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private Paragraph spacer(int points) {
        Paragraph paragraph = new Paragraph(" ");
        paragraph.setLeading(points);
        return paragraph;
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}

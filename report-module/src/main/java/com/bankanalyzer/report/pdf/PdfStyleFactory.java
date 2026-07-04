package com.bankanalyzer.report.pdf;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.springframework.stereotype.Component;

import java.awt.Color;

/**
 * Font/color constants and table-styling helpers shared by the PDF section writers.
 */
@Component
public class PdfStyleFactory {

    public static final Color HEADER_BG = new Color(31, 73, 125);
    public static final Color ALT_ROW_BG = new Color(235, 241, 250);
    public static final Color WHITE = Color.WHITE;
    public static final Color DARK_GRAY = new Color(50, 50, 50);

    public Paragraph sectionHeader(String title) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, HEADER_BG);
        Paragraph p = new Paragraph(title, f);
        p.setSpacingBefore(14);
        p.setSpacingAfter(2);
        return p;
    }

    public void addTableHeader(PdfPTable table, String... headers) {
        Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    public void addRow(PdfPTable table, Color bg, String... values) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 8, DARK_GRAY);
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v, font));
            cell.setBackgroundColor(bg);
            cell.setPadding(4);
            table.addCell(cell);
        }
    }
}

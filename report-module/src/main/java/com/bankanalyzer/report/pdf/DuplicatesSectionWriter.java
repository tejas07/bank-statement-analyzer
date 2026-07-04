package com.bankanalyzer.report.pdf;

import com.bankanalyzer.api.dto.DuplicateGroup;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;

import static com.bankanalyzer.report.pdf.PdfStyleFactory.ALT_ROW_BG;
import static com.bankanalyzer.report.pdf.PdfStyleFactory.WHITE;
import static com.bankanalyzer.report.pdf.ReportFormatting.fmt;
import static com.bankanalyzer.report.pdf.ReportFormatting.truncate;

/**
 * "Possible Duplicate Transactions" table section (only rendered when duplicates exist).
 */
@Component
@RequiredArgsConstructor
public class DuplicatesSectionWriter {

    private final PdfStyleFactory styleFactory;

    public void write(Document doc, List<DuplicateGroup> duplicates) throws DocumentException {
        doc.add(styleFactory.sectionHeader("Possible Duplicate Transactions (" + duplicates.size() + " groups)"));

        Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, new Color(120, 0, 0));
        Paragraph note = new Paragraph(
                "The following transactions appear more than once with the same description and amount.", noteFont);
        note.setSpacingBefore(4);
        note.setSpacingAfter(6);
        doc.add(note);

        PdfPTable table = new PdfPTable(new float[]{40, 15, 15, 10, 20});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setHeaderRows(1);
        styleFactory.addTableHeader(table, "Description", "Debit", "Credit", "Count", "Dates");

        int rowIdx = 0;
        for (DuplicateGroup g : duplicates) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            styleFactory.addRow(table, bg,
                    truncate(g.getDescription(), 55),
                    g.getDebit() > 0 ? fmt(g.getDebit()) : "",
                    g.getCredit() > 0 ? fmt(g.getCredit()) : "",
                    String.valueOf(g.getCount()),
                    String.join(", ", g.getOccurrenceDates()));
        }
        doc.add(table);
    }
}

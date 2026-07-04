package com.bankanalyzer.report.pdf;

import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.TransactionGroups;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfPTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.bankanalyzer.report.pdf.PdfStyleFactory.ALT_ROW_BG;
import static com.bankanalyzer.report.pdf.PdfStyleFactory.WHITE;
import static com.bankanalyzer.report.pdf.ReportFormatting.fmt;

/**
 * "Monthly Breakdown" table section.
 */
@Component
@RequiredArgsConstructor
public class MonthlySectionWriter {

    private final PdfStyleFactory styleFactory;

    public void write(Document doc, List<Transaction> txns) throws DocumentException {
        doc.add(styleFactory.sectionHeader("Monthly Breakdown"));

        TreeMap<String, List<Transaction>> byMonth = TransactionGroups.groupByMonth(txns);

        PdfPTable table = new PdfPTable(new float[]{18, 15, 22, 15, 22});
        table.setWidthPercentage(80);
        table.setSpacingBefore(6);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);
        styleFactory.addTableHeader(table, "Month", "Debit Count", "Total Debit", "Credit Count", "Total Credit");

        int rowIdx = 0;
        for (Map.Entry<String, List<Transaction>> e : byMonth.entrySet()) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            List<Transaction> m = e.getValue();
            styleFactory.addRow(table, bg,
                    e.getKey(),
                    String.valueOf(m.stream().filter(Transaction::isDebit).count()),
                    fmt(TransactionGroups.totalDebit(m)),
                    String.valueOf(m.stream().filter(Transaction::isCredit).count()),
                    fmt(TransactionGroups.totalCredit(m)));
        }
        doc.add(table);
    }
}

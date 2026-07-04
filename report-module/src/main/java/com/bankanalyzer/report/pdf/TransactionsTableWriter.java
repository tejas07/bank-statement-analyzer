package com.bankanalyzer.report.pdf;

import com.bankanalyzer.model.Transaction;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
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
 * "All Transactions" table section.
 */
@Component
@RequiredArgsConstructor
public class TransactionsTableWriter {

    private final PdfStyleFactory styleFactory;

    public void write(Document doc, List<Transaction> txns) throws DocumentException {
        doc.add(styleFactory.sectionHeader("All Transactions"));

        PdfPTable table = new PdfPTable(new float[]{12, 32, 14, 20, 11, 11});
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setHeaderRows(1);

        styleFactory.addTableHeader(table, "Date", "Description", "Mode", "Merchant", "Debit", "Credit");

        int rowIdx = 0;
        for (Transaction t : txns) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            styleFactory.addRow(table, bg,
                    t.getDate() != null ? t.getDate().toString() : "",
                    truncate(t.getDescription(), 60),
                    t.getPaymentMode().getLabel(),
                    truncate(t.getMerchantName(), 28),
                    t.getDebit() > 0 ? fmt(t.getDebit()) : "",
                    t.getCredit() > 0 ? fmt(t.getCredit()) : "");
        }
        doc.add(table);
    }
}

package com.bankanalyzer.report.pdf;

import com.bankanalyzer.model.PaymentMode;
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

import static com.bankanalyzer.report.pdf.PdfStyleFactory.ALT_ROW_BG;
import static com.bankanalyzer.report.pdf.PdfStyleFactory.WHITE;
import static com.bankanalyzer.report.pdf.ReportFormatting.fmt;

/**
 * "Spend by Payment Mode" table section.
 */
@Component
@RequiredArgsConstructor
public class PaymentModeSectionWriter {

    private final PdfStyleFactory styleFactory;

    public void write(Document doc, List<Transaction> txns) throws DocumentException {
        doc.add(styleFactory.sectionHeader("Spend by Payment Mode"));

        Map<PaymentMode, List<Transaction>> byMode = TransactionGroups.groupByPaymentMode(txns);

        PdfPTable table = new PdfPTable(new float[]{25, 15, 25, 25});
        table.setWidthPercentage(70);
        table.setSpacingBefore(6);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);
        styleFactory.addTableHeader(table, "Payment Mode", "Count", "Total Debit", "Total Credit");

        int rowIdx = 0;
        for (Map.Entry<PaymentMode, List<Transaction>> e : byMode.entrySet()) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            styleFactory.addRow(table, bg,
                    e.getKey().getLabel(),
                    String.valueOf(e.getValue().size()),
                    fmt(TransactionGroups.totalDebit(e.getValue())),
                    fmt(TransactionGroups.totalCredit(e.getValue())));
        }
        doc.add(table);
    }
}

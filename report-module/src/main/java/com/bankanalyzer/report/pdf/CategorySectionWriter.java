package com.bankanalyzer.report.pdf;

import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.Transaction;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfPTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bankanalyzer.report.pdf.PdfStyleFactory.ALT_ROW_BG;
import static com.bankanalyzer.report.pdf.PdfStyleFactory.WHITE;
import static com.bankanalyzer.report.pdf.ReportFormatting.fmt;

/**
 * "Spend by Category" table section.
 */
@Component
@RequiredArgsConstructor
public class CategorySectionWriter {

    private final PdfStyleFactory styleFactory;

    public void write(Document doc, List<Transaction> txns) throws DocumentException {
        doc.add(styleFactory.sectionHeader("Spend by Category"));

        Map<Category, Double> byCategory = txns.stream()
                .filter(Transaction::isDebit)
                .collect(Collectors.groupingBy(Transaction::getCategory,
                        Collectors.summingDouble(Transaction::getDebit)));

        List<Map.Entry<Category, Double>> sorted = byCategory.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());

        PdfPTable table = new PdfPTable(new float[]{40, 20, 40});
        table.setWidthPercentage(70);
        table.setSpacingBefore(6);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);
        styleFactory.addTableHeader(table, "Category", "Txn Count", "Total Debit");

        int rowIdx = 0;
        for (Map.Entry<Category, Double> entry : sorted) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            long count = txns.stream().filter(t -> t.isDebit() && t.getCategory() == entry.getKey()).count();
            styleFactory.addRow(table, bg,
                    entry.getKey().name().replace('_', ' '),
                    String.valueOf(count),
                    fmt(entry.getValue()));
        }
        doc.add(table);
    }
}

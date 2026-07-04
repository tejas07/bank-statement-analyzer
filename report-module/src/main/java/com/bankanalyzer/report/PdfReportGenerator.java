package com.bankanalyzer.report;

import com.bankanalyzer.api.dto.CustomerDetails;
import com.bankanalyzer.api.dto.DuplicateGroup;
import com.bankanalyzer.model.DuplicateTransactionFinder;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.report.pdf.CategorySectionWriter;
import com.bankanalyzer.report.pdf.DuplicatesSectionWriter;
import com.bankanalyzer.report.pdf.MonthlySectionWriter;
import com.bankanalyzer.report.pdf.PaymentModeSectionWriter;
import com.bankanalyzer.report.pdf.ReportHeaderFooterEvent;
import com.bankanalyzer.report.pdf.TitleSectionWriter;
import com.bankanalyzer.report.pdf.TransactionsTableWriter;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Orchestrates a multi-section PDF report using OpenPDF.
 * <p>
 * Sections:
 * 1. Title / Summary (bank, dates, totals, customer details)
 * 2. All Transactions table
 * 3. Spend by Category
 * 4. Spend by Payment Mode
 * 5. Monthly Breakdown
 * 6. Duplicate Transactions (if any)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfReportGenerator {

    private final TitleSectionWriter titleSectionWriter;
    private final TransactionsTableWriter transactionsTableWriter;
    private final CategorySectionWriter categorySectionWriter;
    private final PaymentModeSectionWriter paymentModeSectionWriter;
    private final MonthlySectionWriter monthlySectionWriter;
    private final DuplicatesSectionWriter duplicatesSectionWriter;

    public byte[] generateBytes(List<Transaction> transactions) throws IOException {
        return generateBytes(transactions, null);
    }

    public byte[] generateBytes(List<Transaction> transactions,
                                CustomerDetails customerDetails) throws IOException {
        log.info("Generating PDF report with {} transactions", transactions.size());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 54, 36);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);
        writer.setPageEvent(new ReportHeaderFooterEvent());
        doc.open();

        titleSectionWriter.write(doc, transactions, customerDetails);
        doc.add(Chunk.NEWLINE);

        transactionsTableWriter.write(doc, transactions);
        doc.newPage();

        categorySectionWriter.write(doc, transactions);
        doc.newPage();

        paymentModeSectionWriter.write(doc, transactions);
        doc.add(Chunk.NEWLINE);
        monthlySectionWriter.write(doc, transactions);

        List<DuplicateGroup> duplicates = DuplicateTransactionFinder.find(transactions);
        if (!duplicates.isEmpty()) {
            doc.newPage();
            duplicatesSectionWriter.write(doc, duplicates);
        }

        doc.close();
        return bos.toByteArray();
    }
}

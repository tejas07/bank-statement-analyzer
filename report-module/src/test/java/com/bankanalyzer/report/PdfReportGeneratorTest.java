package com.bankanalyzer.report;

import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.PaymentMode;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.report.pdf.CategorySectionWriter;
import com.bankanalyzer.report.pdf.DuplicatesSectionWriter;
import com.bankanalyzer.report.pdf.MonthlySectionWriter;
import com.bankanalyzer.report.pdf.PaymentModeSectionWriter;
import com.bankanalyzer.report.pdf.PdfStyleFactory;
import com.bankanalyzer.report.pdf.TitleSectionWriter;
import com.bankanalyzer.report.pdf.TransactionsTableWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural smoke test pinning current {@link PdfReportGenerator} output
 * (Phase 0 safety net) — must still produce a valid, multi-page PDF after
 * the Phase 1.5 per-section-writer split.
 */
public class PdfReportGeneratorTest {

    private final PdfStyleFactory styleFactory = new PdfStyleFactory();

    private final PdfReportGenerator generator = new PdfReportGenerator(
            new TitleSectionWriter(styleFactory),
            new TransactionsTableWriter(styleFactory),
            new CategorySectionWriter(styleFactory),
            new PaymentModeSectionWriter(styleFactory),
            new MonthlySectionWriter(styleFactory),
            new DuplicatesSectionWriter(styleFactory));

    @Test
    void generatesValidMultiPagePdf() throws Exception {
        byte[] bytes = generator.generateBytes(fixture());

        assertTrue(bytes.length > 0);
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            // title+txn table page, category page, payment-mode+monthly page = 3 pages (no duplicates in fixture)
            assertEquals(3, doc.getNumberOfPages());
        }
    }

    private List<Transaction> fixture() {
        return List.of(
                Transaction.builder().date(LocalDate.of(2024, 1, 5)).description("Grocery run")
                        .debit(1500).credit(0).balance(50000)
                        .paymentMode(PaymentMode.UPI).merchantName("Bigbasket").category(Category.GROCERIES).build(),
                Transaction.builder().date(LocalDate.of(2024, 1, 10)).description("Salary")
                        .debit(0).credit(80000).balance(130000)
                        .paymentMode(PaymentMode.NEFT).merchantName("Employer").category(Category.SALARY_INCOME).build(),
                Transaction.builder().date(LocalDate.of(2024, 2, 3)).description("Fuel")
                        .debit(2000).credit(0).balance(128000)
                        .paymentMode(PaymentMode.CARD_POS).merchantName("HPCL").category(Category.FUEL).build()
        );
    }

    @Test
    void addsDuplicatesPageWhenDuplicateTransactionsExist() throws Exception {
        List<Transaction> withDuplicates = List.of(
                Transaction.builder().date(LocalDate.of(2024, 1, 5)).description("Netflix")
                        .debit(500).credit(0).balance(1000)
                        .paymentMode(PaymentMode.CARD_POS).merchantName("Netflix").category(Category.ENTERTAINMENT).build(),
                Transaction.builder().date(LocalDate.of(2024, 1, 6)).description("Netflix")
                        .debit(500).credit(0).balance(500)
                        .paymentMode(PaymentMode.CARD_POS).merchantName("Netflix").category(Category.ENTERTAINMENT).build()
        );

        byte[] bytes = generator.generateBytes(withDuplicates);

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            assertEquals(4, doc.getNumberOfPages());
        }
    }
}

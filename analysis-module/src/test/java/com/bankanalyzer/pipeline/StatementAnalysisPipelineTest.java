package com.bankanalyzer.pipeline;

import com.bankanalyzer.analyzer.TransactionAnalyzer;
import com.bankanalyzer.api.dto.SummaryResponse;
import com.bankanalyzer.model.CustomerDetails;
import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.StatementType;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.entity.StatementUploadEntity;
import com.bankanalyzer.parser.BankParserRegistry;
import com.bankanalyzer.parser.BankStatementParser;
import com.bankanalyzer.report.ExcelReportGenerator;
import com.bankanalyzer.report.PdfReportGenerator;
import com.bankanalyzer.report.excel.CustomerDetailsSheetWriter;
import com.bankanalyzer.report.excel.ExcelChartBuilder;
import com.bankanalyzer.report.excel.ExcelStyleFactory;
import com.bankanalyzer.report.excel.MerchantSheetWriter;
import com.bankanalyzer.report.excel.MonthSheetWriter;
import com.bankanalyzer.report.excel.PaymentModeSheetWriter;
import com.bankanalyzer.report.excel.TransactionsSheetWriter;
import com.bankanalyzer.report.pdf.CategorySectionWriter;
import com.bankanalyzer.report.pdf.DuplicatesSectionWriter;
import com.bankanalyzer.report.pdf.MonthlySectionWriter;
import com.bankanalyzer.report.pdf.PaymentModeSectionWriter;
import com.bankanalyzer.report.pdf.PdfStyleFactory;
import com.bankanalyzer.report.pdf.TitleSectionWriter;
import com.bankanalyzer.report.pdf.TransactionsTableWriter;
import com.bankanalyzer.service.CategoryTagger;
import com.bankanalyzer.service.DuplicateDetector;
import com.bankanalyzer.service.InsightService;
import com.bankanalyzer.service.PersistenceGateway;
import com.bankanalyzer.service.SummaryBuilder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization test for {@link DefaultStatementAnalysisPipeline} (Phase 1.1/1.7
 * safety net) — pins that all entry points (summary, XLSX report, PDF report) run the
 * same parse -> analyze -> persist sequence, using a stub parser (real PDF parsing is
 * exercised separately by {@code BankStatementParserTest}) and a fake persistence gateway.
 */
public class StatementAnalysisPipelineTest {

    private final StubParser parser = new StubParser();
    private final TransactionAnalyzer analyzer = new TransactionAnalyzer(new CategoryTagger());
    private final FakePersistenceGateway persistenceGateway = new FakePersistenceGateway();
    private final SummaryBuilder summaryBuilder =
            new SummaryBuilder(analyzer, new InsightService(), new DuplicateDetector());
    private final ExcelStyleFactory excelStyleFactory = new ExcelStyleFactory();
    private final ExcelChartBuilder excelChartBuilder = new ExcelChartBuilder();
    private final ExcelReportGenerator excelReportGenerator = new ExcelReportGenerator(
            new CustomerDetailsSheetWriter(excelStyleFactory),
            new TransactionsSheetWriter(excelStyleFactory),
            new PaymentModeSheetWriter(excelStyleFactory, excelChartBuilder),
            new MerchantSheetWriter(excelStyleFactory, excelChartBuilder),
            new MonthSheetWriter(excelStyleFactory, excelChartBuilder));
    private final PdfStyleFactory pdfStyleFactory = new PdfStyleFactory();
    private final PdfReportGenerator pdfReportGenerator = new PdfReportGenerator(
            new TitleSectionWriter(pdfStyleFactory),
            new TransactionsTableWriter(pdfStyleFactory),
            new CategorySectionWriter(pdfStyleFactory),
            new PaymentModeSectionWriter(pdfStyleFactory),
            new MonthlySectionWriter(pdfStyleFactory),
            new DuplicatesSectionWriter(pdfStyleFactory));
    private final StatementAnalysisPipeline pipeline = new DefaultStatementAnalysisPipeline(
            parser, analyzer, summaryBuilder, persistenceGateway, excelReportGenerator, pdfReportGenerator);

    @Test
    void analyzeAndPersistBuildsSummaryAndAttachesUploadId() throws Exception {
        SummaryResponse summary = pipeline.analyzeAndPersist("fake pdf bytes".getBytes(), "statement.pdf");

        assertEquals(42L, summary.getUploadId());
        assertEquals("Stub Bank", summary.getDetectedBank());
        assertEquals(1, summary.getTotalTransactions());
        assertEquals(1, persistenceGateway.savedHashes.size());
    }

    @Test
    void buildSummaryCachedBuildsSummaryAndPersists() throws Exception {
        SummaryResponse summary = pipeline.buildSummaryCached("hash1", "fake pdf bytes".getBytes(), "statement.pdf");

        assertEquals(42L, summary.getUploadId());
        assertEquals("Stub Bank", summary.getDetectedBank());
        assertEquals(1, persistenceGateway.savedHashes.size());
    }

    @Test
    void buildExcelReportCachedProducesXlsxBytesAndPersists() throws Exception {
        byte[] bytes = pipeline.buildExcelReportCached("hash2", "fake pdf bytes".getBytes(), "statement.pdf");

        assertTrue(bytes.length > 0);
        assertEquals(1, persistenceGateway.savedHashes.size());
    }

    @Test
    void buildPdfReportCachedProducesPdfBytesAndPersists() throws Exception {
        byte[] bytes = pipeline.buildPdfReportCached("hash3", "fake pdf bytes".getBytes(), "statement.pdf");

        assertTrue(bytes.length > 0);
        assertEquals(1, persistenceGateway.savedHashes.size());
    }

    private static class StubParser extends BankStatementParser {
        StubParser() {
            super(new BankParserRegistry(List.of()));
        }

        @Override
        public ParseResult parseWithMeta(InputStream pdfStream) {
            Transaction txn = Transaction.builder()
                    .date(LocalDate.of(2024, 1, 5))
                    .description("Test Txn").debit(100).credit(0).balance(900)
                    .build();
            return new ParseResult(List.of(txn), "Stub Bank",
                    StatementType.SAVINGS_ACCOUNT, CustomerDetails.builder().customerName("Jane Doe").build());
        }
    }

    private static class FakePersistenceGateway implements PersistenceGateway {
        final List<String> savedHashes = new ArrayList<>();

        @Override
        public Optional<StatementUploadEntity> findDuplicate(String fileHash) {
            return Optional.empty();
        }

        @Override
        public Long save(String fileHash, String originalFilename, ParseResult parseResult,
                         List<Transaction> enriched) {
            savedHashes.add(fileHash);
            return 42L;
        }
    }
}

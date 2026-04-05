package com.bankanalyzer;

import com.bankanalyzer.analyzer.TransactionAnalyzer;
import com.bankanalyzer.api.dto.DuplicateGroup;
import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.parser.BankParserRegistry;
import com.bankanalyzer.parser.BankStatementParser;
import com.bankanalyzer.parser.impl.GenericBankParser;
import com.bankanalyzer.parser.impl.IciciCreditCardParser;
import com.bankanalyzer.parser.impl.IciciSavingsParser;
import com.bankanalyzer.parser.impl.SbiParser;
import com.bankanalyzer.report.ExcelReportGenerator;
import com.bankanalyzer.report.PdfReportGenerator;
import com.bankanalyzer.service.CategoryTagger;
import com.bankanalyzer.service.DuplicateDetector;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * CLI entry point for the Bank Statement Analyzer (no Spring context).
 *
 * Usage:
 *   java -jar bank-statement-analyzer-1.0.0.jar <input.pdf> [output] [--pdf]
 *
 * Options:
 *   output   Path for the output file (default: same dir as input, .xlsx or .pdf extension)
 *   --pdf    Generate a PDF report instead of the default XLSX
 *
 * Examples:
 *   java -jar bank-statement-analyzer.jar statement.pdf
 *   java -jar bank-statement-analyzer.jar statement.pdf report.xlsx
 *   java -jar bank-statement-analyzer.jar statement.pdf report.pdf --pdf
 */
@Slf4j
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: bank-statement-analyzer <input.pdf> [output] [--pdf]");
            System.exit(1);
        }

        File inputPdf = new File(args[0]);
        if (!inputPdf.exists() || !inputPdf.isFile()) {
            System.err.println("ERROR: File not found: " + inputPdf.getAbsolutePath());
            System.exit(1);
        }

        boolean pdfMode = false;
        String outputArg = null;
        for (int i = 1; i < args.length; i++) {
            if ("--pdf".equalsIgnoreCase(args[i])) pdfMode = true;
            else outputArg = args[i];
        }

        String extension = pdfMode ? ".pdf" : ".xlsx";
        File outputFile = outputArg != null
            ? new File(outputArg)
            : new File(inputPdf.getParent(),
                inputPdf.getName().replaceAll("(?i)\\.pdf$", "") + "_report" + extension);

        log.info("Input  : {}", inputPdf.getAbsolutePath());
        log.info("Output : {}", outputFile.getAbsolutePath());
        log.info("Format : {}", pdfMode ? "PDF" : "XLSX");

        // ── Step 1: Parse PDF (wire parsers manually — no Spring context) ─────
        CategoryTagger tagger = new CategoryTagger();
        BankParserRegistry registry = new BankParserRegistry(List.of(
            new IciciCreditCardParser(),
            new IciciSavingsParser(),
            new SbiParser(),
            new GenericBankParser()
        ));
        BankStatementParser parser = new BankStatementParser(registry);
        ParseResult parsed = parser.parseWithMeta(inputPdf);

        System.out.printf("Bank detected  : %s%n", parsed.getBankName());
        System.out.printf("Statement type : %s%n", parsed.getStatementType());
        System.out.printf("Transactions   : %d%n", parsed.getTransactions().size());

        if (parsed.getTransactions().isEmpty()) {
            log.warn("No transactions found. Use /api/analyze/raw-text to inspect extraction output.");
        }

        // ── Step 2: Enrich (payment mode + merchant + category) ───────────────
        TransactionAnalyzer analyzer = new TransactionAnalyzer(tagger);
        List<Transaction> enriched = analyzer.analyze(parsed.getTransactions());

        // ── Step 3: Duplicate detection ───────────────────────────────────────
        DuplicateDetector detector = new DuplicateDetector();
        List<DuplicateGroup> duplicates = detector.detect(enriched);
        if (!duplicates.isEmpty()) {
            System.out.printf("Duplicates     : %d group(s) found%n", duplicates.size());
            for (DuplicateGroup g : duplicates) {
                System.out.printf("  [x%d] %s  debit=%.2f  credit=%.2f  dates=%s%n",
                    g.getCount(), g.getDescription(),
                    g.getDebit(), g.getCredit(), g.getOccurrenceDates());
            }
        }

        // ── Step 4: Generate report ───────────────────────────────────────────
        if (pdfMode) {
            PdfReportGenerator pdfGen = new PdfReportGenerator(analyzer, detector);
            byte[] pdfBytes = pdfGen.generateBytes(enriched);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(pdfBytes);
            }
        } else {
            ExcelReportGenerator xlsxGen = new ExcelReportGenerator(analyzer);
            xlsxGen.generate(enriched, outputFile);
        }

        System.out.println("Done! Report saved to: " + outputFile.getAbsolutePath());
    }
}

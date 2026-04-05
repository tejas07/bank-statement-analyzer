package com.bankanalyzer.parser;

import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Orchestrator: extracts text from a PDF, sanitizes it, resolves the
 * correct BankParser via the registry, and delegates parsing to it.
 *
 * Public API is unchanged — callers only interact with this class.
 */
@Slf4j
@Component
public class BankStatementParser {

    private final BankParserRegistry registry;

    public BankStatementParser(BankParserRegistry registry) {
        this.registry = registry;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public String extractRawText(InputStream pdfStream) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfStream.readAllBytes())) {
            return newStripper().getText(doc);
        }
    }

    public List<Transaction> parse(InputStream pdfStream) throws IOException {
        return parseWithMeta(pdfStream).getTransactions();
    }

    public ParseResult parseWithMeta(InputStream pdfStream) throws IOException {
        log.info("Parsing PDF from stream");
        try (PDDocument doc = Loader.loadPDF(pdfStream.readAllBytes())) {
            String raw = newStripper().getText(doc);
            log.debug("Extracted {} characters from PDF stream", raw.length());
            return dispatchWithMeta(raw);
        }
    }

    public List<Transaction> parse(File pdfFile) throws IOException {
        return parseWithMeta(pdfFile).getTransactions();
    }

    public ParseResult parseWithMeta(File pdfFile) throws IOException {
        log.info("Opening PDF: {}", pdfFile.getAbsolutePath());
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            String raw = newStripper().getText(doc);
            log.debug("Extracted {} characters from PDF", raw.length());
            return dispatchWithMeta(raw);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private List<Transaction> dispatch(String raw) {
        return dispatchWithMeta(raw).getTransactions();
    }

    private ParseResult dispatchWithMeta(String raw) {
        String text = sanitize(raw);
        BankParser parser = registry.resolve(text);
        log.info("Auto-detected parser: {} ({})", parser.bankName(), parser.statementType());
        List<Transaction> txns = parser.parse(text);
        com.bankanalyzer.api.dto.CustomerDetails customerDetails = parser.extractCustomerDetails(raw);
        return new ParseResult(txns, parser.bankName(), parser.statementType(), customerDetails);
    }

    public String sanitize(String raw) {
        return raw
            .replace("₹", "")
            .replace("–", "-")
            .replace("—", "-")
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
            .replaceAll("[ \\t]+", " ");
    }

    private PDFTextStripper newStripper() throws IOException {
        PDFTextStripper s = new PDFTextStripper();
        s.setSortByPosition(true);
        s.setSuppressDuplicateOverlappingText(false);
        return s;
    }
}

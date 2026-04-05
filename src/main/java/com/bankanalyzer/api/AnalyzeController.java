package com.bankanalyzer.api;

import com.bankanalyzer.analyzer.TransactionAnalyzer;
import com.bankanalyzer.api.dto.*;
import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.entity.StatementUploadEntity;
import com.bankanalyzer.parser.BankStatementParser;
import com.bankanalyzer.report.ExcelReportGenerator;
import com.bankanalyzer.report.PdfReportGenerator;
import com.bankanalyzer.service.AsyncJobService;
import com.bankanalyzer.service.PersistenceGateway;
import com.bankanalyzer.service.SummaryBuilder;
import com.bankanalyzer.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for bank statement analysis.
 *
 * Endpoints:
 *   GET  /api/health                        — health check
 *   POST /api/analyze/summary               — upload PDF → JSON summary (Feature 1)
 *   POST /api/analyze/report                — upload PDF → XLSX (Feature 1)
 *   POST /api/analyze/pdf-report            — upload PDF → PDF report (Feature 5)
 *   POST /api/analyze/raw-text              — upload PDF → raw text (debug)
 *   POST /api/analyze/multi/summary         — upload multiple PDFs → merged JSON summary (Feature 2)
 *   POST /api/analyze/multi/report          — upload multiple PDFs → merged XLSX (Feature 2)
 *   POST /api/analyze/submit                — async job submission (Feature 8)
 *   GET  /api/analyze/status/{jobId}        — async job polling (Feature 8)
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_MIME  = "application/pdf";

    private final BankStatementParser  parser;
    private final TransactionAnalyzer  analyzer;
    private final ExcelReportGenerator excelReportGenerator;
    private final PdfReportGenerator   pdfReportGenerator;
    private final SummaryBuilder       summaryBuilder;
    private final AsyncJobService      asyncJobService;
    private final PersistenceGateway   persistenceGateway;
    private final WebhookService       webhookService;
    private final CacheManager         cacheManager;

    public AnalyzeController(BankStatementParser parser,
                              TransactionAnalyzer analyzer,
                              ExcelReportGenerator excelReportGenerator,
                              PdfReportGenerator pdfReportGenerator,
                              SummaryBuilder summaryBuilder,
                              AsyncJobService asyncJobService,
                              PersistenceGateway persistenceGateway,
                              WebhookService webhookService,
                              CacheManager cacheManager) {
        this.parser               = parser;
        this.analyzer             = analyzer;
        this.excelReportGenerator = excelReportGenerator;
        this.pdfReportGenerator   = pdfReportGenerator;
        this.summaryBuilder       = summaryBuilder;
        this.asyncJobService      = asyncJobService;
        this.persistenceGateway   = persistenceGateway;
        this.webhookService       = webhookService;
        this.cacheManager         = cacheManager;
    }

    // ── Health ────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "bank-statement-analyzer"));
    }

    // ── Single-file summary ───────────────────────────────────────────────────

    @PostMapping(value = "/analyze/summary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SummaryResponse> getSummary(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "webhookUrl", required = false) String webhookUrl)
            throws IOException {

        validateFile(file);
        log.info("Summary request — file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        byte[] fileBytes = file.getBytes();
        String hash      = DigestUtils.md5DigestAsHex(fileBytes);

        Optional<StatementUploadEntity> duplicate = persistenceGateway.findDuplicate(hash);
        if (duplicate.isPresent()) {
            StatementUploadEntity prev = duplicate.get();
            log.warn("Duplicate upload detected — hash {} previously uploaded at {}", hash, prev.getUploadedAt());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(SummaryResponse.builder()
                    .uploadId(prev.getId())
                    .detectedBank(prev.getBankName())
                    .totalTransactions(prev.getTransactionCount())
                    .build());
        }

        Cache cache = cacheManager.getCache("analysis");
        SummaryResponse summary = cache == null ? null : cache.get(hash + ":summary", SummaryResponse.class);

        if (summary == null) {
            ParseResult       parsed   = parser.parseWithMeta(new ByteArrayInputStream(fileBytes));
            List<Transaction> enriched = analyzer.analyze(parsed.getTransactions());
            summary  = summaryBuilder.build(enriched, parsed);
            Long uploadId = persistenceGateway.save(hash, file.getOriginalFilename(), parsed, enriched);
            summary  = summary.toBuilder().uploadId(uploadId).build();
            if (cache != null) cache.put(hash + ":summary", summary);
            log.info("Summary generated — uploadId={}, bank={}", uploadId, parsed.getBankName());
        } else {
            log.info("Summary cache hit for hash {}", hash);
        }

        if (webhookUrl != null && !webhookUrl.isBlank()) {
            webhookService.notify(webhookUrl, summary);
        }
        return ResponseEntity.ok(summary);
    }

    // ── Single-file XLSX report ───────────────────────────────────────────────

    @PostMapping(value = "/analyze/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void downloadReport(@RequestParam("file") MultipartFile file,
                                HttpServletResponse response) throws IOException {
        validateFile(file);
        log.info("Report (XLSX) request — file: {}", file.getOriginalFilename());

        byte[] fileBytes = file.getBytes();
        String hash      = DigestUtils.md5DigestAsHex(fileBytes);

        Optional<StatementUploadEntity> duplicate = persistenceGateway.findDuplicate(hash);
        if (duplicate.isPresent()) {
            response.sendError(HttpStatus.CONFLICT.value(),
                "Already processed on " + duplicate.get().getUploadedAt() +
                " (uploadId=" + duplicate.get().getId() + ")");
            return;
        }

        Cache  cache     = cacheManager.getCache("analysis");
        byte[] xlsxBytes = cache == null ? null : cache.get(hash + ":report", byte[].class);

        if (xlsxBytes == null) {
            ParseResult       parsed   = parser.parseWithMeta(new ByteArrayInputStream(fileBytes));
            List<Transaction> enriched = analyzer.analyze(parsed.getTransactions());
            xlsxBytes = excelReportGenerator.generateBytes(enriched, parsed.getCustomerDetails());
            persistenceGateway.save(hash, file.getOriginalFilename(), parsed, enriched);
            if (cache != null) cache.put(hash + ":report", xlsxBytes);
        }

        writeFileResponse(response, XLSX_MIME,
            resolveFilename(file.getOriginalFilename(), "_report.xlsx"), xlsxBytes);
    }

    // ── Single-file PDF report (Feature 5) ───────────────────────────────────

    @PostMapping(value = "/analyze/pdf-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void downloadPdfReport(@RequestParam("file") MultipartFile file,
                                   HttpServletResponse response) throws IOException {
        validateFile(file);
        log.info("PDF report request — file: {}", file.getOriginalFilename());

        byte[] fileBytes = file.getBytes();
        String hash      = DigestUtils.md5DigestAsHex(fileBytes);

        Cache  cache    = cacheManager.getCache("analysis");
        byte[] pdfBytes = cache == null ? null : cache.get(hash + ":pdf", byte[].class);

        if (pdfBytes == null) {
            ParseResult       parsed   = parser.parseWithMeta(new ByteArrayInputStream(fileBytes));
            List<Transaction> enriched = analyzer.analyze(parsed.getTransactions());
            pdfBytes = pdfReportGenerator.generateBytes(enriched, parsed.getCustomerDetails());
            persistenceGateway.save(hash, file.getOriginalFilename(), parsed, enriched);
            if (cache != null) cache.put(hash + ":pdf", pdfBytes);
            log.info("PDF report generated for {}", file.getOriginalFilename());
        }

        writeFileResponse(response, PDF_MIME,
            resolveFilename(file.getOriginalFilename(), "_report.pdf"), pdfBytes);
    }

    // ── Multi-file summary (Feature 2) ────────────────────────────────────────

    @PostMapping(value = "/analyze/multi/summary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SummaryResponse> getMultiSummary(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "webhookUrl", required = false) String webhookUrl)
            throws IOException {

        validateFiles(files);
        log.info("Multi-summary request — {} files", files.size());

        List<Transaction> allTransactions = new ArrayList<>();
        List<String>      detectedBanks   = new ArrayList<>();
        ParseResult       firstParsed     = null;

        for (MultipartFile file : files) {
            validateFile(file);
            ParseResult       parsed   = parser.parseWithMeta(new ByteArrayInputStream(file.getBytes()));
            List<Transaction> enriched = analyzer.analyze(parsed.getTransactions());
            allTransactions.addAll(enriched);
            detectedBanks.add(parsed.getBankName());
            if (firstParsed == null) firstParsed = parsed;
            log.info("  Parsed '{}' — bank={}, txns={}",
                file.getOriginalFilename(), parsed.getBankName(), enriched.size());
        }

        // Sort merged transactions chronologically
        allTransactions.sort(Comparator.comparing(t -> t.getDate() != null ? t.getDate()
            : java.time.LocalDate.MIN));

        String bankLabel = detectedBanks.stream().distinct().collect(Collectors.joining(", "));
        SummaryResponse summary = summaryBuilder.build(allTransactions, firstParsed, bankLabel)
            .toBuilder()
            .detectedBanks(detectedBanks)
            .build();

        if (webhookUrl != null && !webhookUrl.isBlank()) {
            webhookService.notify(webhookUrl, summary);
        }
        return ResponseEntity.ok(summary);
    }

    // ── Multi-file XLSX report (Feature 2) ───────────────────────────────────

    @PostMapping(value = "/analyze/multi/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void downloadMultiReport(@RequestParam("files") List<MultipartFile> files,
                                     HttpServletResponse response) throws IOException {
        validateFiles(files);
        log.info("Multi-report (XLSX) request — {} files", files.size());

        List<Transaction> allTransactions = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);
            ParseResult       parsed   = parser.parseWithMeta(new ByteArrayInputStream(file.getBytes()));
            List<Transaction> enriched = analyzer.analyze(parsed.getTransactions());
            allTransactions.addAll(enriched);
        }
        allTransactions.sort(Comparator.comparing(t -> t.getDate() != null ? t.getDate()
            : java.time.LocalDate.MIN));

        byte[] xlsxBytes = excelReportGenerator.generateBytes(allTransactions);
        writeFileResponse(response, XLSX_MIME, "merged_report.xlsx", xlsxBytes);
    }

    // ── Raw text (debug) ──────────────────────────────────────────────────────

    @PostMapping(value = "/analyze/raw-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRawText(@RequestParam("file") MultipartFile file)
            throws IOException {
        validateFile(file);
        return ResponseEntity.ok(parser.extractRawText(new ByteArrayInputStream(file.getBytes())));
    }

    // ── Async submit (Feature 8) ──────────────────────────────────────────────

    @PostMapping(value = "/analyze/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitJobResponse> submitJob(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws IOException {

        validateFile(file);
        log.info("Async job submit — file: {}", file.getOriginalFilename());

        String baseUrl = request.getScheme() + "://" + request.getServerName()
            + ":" + request.getServerPort();

        SubmitJobResponse resp = asyncJobService.submit(
            file.getBytes(), file.getOriginalFilename(), baseUrl);
        return ResponseEntity.accepted().body(resp);
    }

    // ── Async status poll (Feature 8) ─────────────────────────────────────────

    @GetMapping("/analyze/status/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
        JobStatusResponse resp = asyncJobService.getStatus(jobId);
        HttpStatus status = switch (resp.getStatus()) {
            case DONE    -> HttpStatus.OK;
            case FAILED  -> HttpStatus.INTERNAL_SERVER_ERROR;
            default      -> HttpStatus.ACCEPTED;  // PENDING / PROCESSING
        };
        return ResponseEntity.status(status).body(resp);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded. Provide a PDF via the 'file' field.");
        }
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported. Received: " + name);
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files uploaded.");
        }
        if (files.size() > 10) {
            throw new IllegalArgumentException("Maximum 10 files per request.");
        }
    }

    private void writeFileResponse(HttpServletResponse response, String mime,
                                    String filename, byte[] bytes) throws IOException {
        response.setContentType(mime);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-Control", "no-cache");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    private String resolveFilename(String original, String suffix) {
        if (original == null || original.isBlank()) return "bank" + suffix;
        return original.replaceAll("(?i)\\.pdf$", "") + suffix;
    }
}

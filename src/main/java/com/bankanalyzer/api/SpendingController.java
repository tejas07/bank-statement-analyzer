package com.bankanalyzer.api;

import com.bankanalyzer.analyzer.TransactionAnalyzer;
import com.bankanalyzer.api.contract.SpendingApi;
import com.bankanalyzer.api.dto.*;
import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.parser.BankStatementParser;
import com.bankanalyzer.service.ForecastService;
import com.bankanalyzer.service.SpendingAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/spending")
@RequiredArgsConstructor
public class SpendingController implements SpendingApi {

    private final BankStatementParser      parser;
    private final TransactionAnalyzer      analyzer;
    private final SpendingAnalyticsService analyticsService;
    private final ForecastService          forecastService;

    // ── Category spending breakdown ───────────────────────────────────────────

    @Override
    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CategorySpendingResponse> getCategorySpending(
            @RequestParam("file") MultipartFile file) throws IOException {

        validateFile(file);
        log.info("Category spending request — file: {}", file.getOriginalFilename());

        List<Transaction> enriched = parse(file);
        CategorySpendingResponse response = analyticsService.buildCategorySpending(enriched);

        log.info("Category spending built — totalSpend={}, months={}",
            response.getTotalSpend(), response.getTotalMonths());
        return ResponseEntity.ok(response);
    }

    // ── Spending forecast ─────────────────────────────────────────────────────

    @Override
    @PostMapping(value = "/forecast", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SpendingForecastResponse> getForecast(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "months", defaultValue = "6") int months,
            @RequestParam(value = "inflationRate", defaultValue = "6.0") double inflationRate)
            throws IOException {

        validateFile(file);
        validateForecastParams(months, inflationRate);
        log.info("Forecast request — file: {}, months: {}, inflationRate: {}%",
            file.getOriginalFilename(), months, inflationRate);

        List<Transaction> enriched = parse(file);
        SpendingForecastResponse response = forecastService.forecast(enriched, months, inflationRate);

        log.info("Forecast built — totalPotentialSavings={}", response.getTotalPotentialSavings());
        return ResponseEntity.ok(response);
    }

    // ── Productivity insights ─────────────────────────────────────────────────

    @Override
    @PostMapping(value = "/productivity", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductivityInsightsResponse> getProductivityInsights(
            @RequestParam("file") MultipartFile file) throws IOException {

        validateFile(file);
        log.info("Productivity insights request — file: {}", file.getOriginalFilename());

        List<Transaction> enriched = parse(file);
        ProductivityInsightsResponse response = analyticsService.buildProductivityInsights(enriched);

        log.info("Productivity insights built — healthScore={}, recommendations={}",
            response.getFinancialHealthScore(), response.getRecommendations().size());
        return ResponseEntity.ok(response);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Transaction> parse(MultipartFile file) throws IOException {
        ParseResult parsed = parser.parseWithMeta(new ByteArrayInputStream(file.getBytes()));
        return analyzer.analyze(parsed.getTransactions());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded. Provide a PDF via the 'file' field.");
        }
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported. Received: " + name);
        }
    }

    private void validateForecastParams(int months, double inflationRate) {
        if (months < 1 || months > 24) {
            throw new IllegalArgumentException("'months' must be between 1 and 24.");
        }
        if (inflationRate < 0 || inflationRate > 50) {
            throw new IllegalArgumentException("'inflationRate' must be between 0 and 50 (%).");
        }
    }
}

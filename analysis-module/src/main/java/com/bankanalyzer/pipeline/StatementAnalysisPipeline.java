package com.bankanalyzer.pipeline;

import com.bankanalyzer.api.dto.SummaryResponse;

import java.io.IOException;

/**
 * Single source of truth for "parse → analyze → persist" — shared by the
 * synchronous REST path ({@code AnalyzeController}) and the async Kafka path
 * ({@code AnalysisJobConsumer}), which previously duplicated this sequence.
 *
 * <p>The {@code *Cached} methods are keyed by the caller-supplied file hash and
 * replace the manual {@code CacheManager.getCache("analysis").get/put(...)} calls
 * that used to live in {@code AnalyzeController}.
 */
public interface StatementAnalysisPipeline {

    /**
     * Parses, enriches, and persists — used by the async path (no caching).
     */
    SummaryResponse analyzeAndPersist(byte[] fileBytes, String originalFilename) throws IOException;

    /**
     * Cached: parses, enriches, persists, and builds the full {@link SummaryResponse}.
     */
    SummaryResponse buildSummaryCached(String hash, byte[] fileBytes, String originalFilename) throws IOException;

    /**
     * Cached: parses, enriches, persists, and builds the XLSX report bytes.
     */
    byte[] buildExcelReportCached(String hash, byte[] fileBytes, String originalFilename) throws IOException;

    /**
     * Cached: parses, enriches, persists, and builds the PDF report bytes.
     */
    byte[] buildPdfReportCached(String hash, byte[] fileBytes, String originalFilename) throws IOException;
}

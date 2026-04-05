package com.bankanalyzer.service;

import com.bankanalyzer.analyzer.TransactionAnalyzer;
import com.bankanalyzer.api.dto.JobStatusResponse;
import com.bankanalyzer.api.dto.SubmitJobResponse;
import com.bankanalyzer.api.dto.SummaryResponse;
import com.bankanalyzer.model.JobStatus;
import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.parser.BankStatementParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 8: Async analysis job service.
 *
 * Clients submit a file via POST /api/analyze/submit and receive a jobId.
 * They then poll GET /api/analyze/status/{jobId} until status is DONE or FAILED.
 * Completed jobs are purged automatically after JOB_TTL_SECONDS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncJobService {

    private static final long JOB_TTL_SECONDS = 3600; // 1 hour
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC);

    private final BankStatementParser parser;
    private final TransactionAnalyzer analyzer;
    private final SummaryBuilder      summaryBuilder;
    private final PersistenceGateway  persistenceGateway;

    // In-memory job store — good enough for single-instance; swap for Redis for multi-node
    private final ConcurrentHashMap<String, JobEntry> jobs = new ConcurrentHashMap<>();

    // ── Submit ────────────────────────────────────────────────────────────────

    public SubmitJobResponse submit(byte[] fileBytes, String originalFilename, String baseUrl) {
        String jobId = UUID.randomUUID().toString();
        jobs.put(jobId, JobEntry.pending(originalFilename));
        log.info("Job {} submitted for file '{}'", jobId, originalFilename);

        processAsync(jobId, fileBytes, originalFilename);

        return SubmitJobResponse.builder()
            .jobId(jobId)
            .statusUrl(baseUrl + "/api/analyze/status/" + jobId)
            .message("Job accepted. Poll statusUrl for results.")
            .build();
    }

    // ── Async processing ──────────────────────────────────────────────────────

    @Async("webhookExecutor")  // reuse existing async thread pool
    protected void processAsync(String jobId, byte[] fileBytes, String originalFilename) {
        jobs.computeIfPresent(jobId, (id, e) -> e.toProcessing());
        log.info("Job {} processing started", jobId);
        try {
            ParseResult     parsed   = parser.parseWithMeta(new ByteArrayInputStream(fileBytes));
            List<Transaction> enriched = analyzer.analyze(parsed.getTransactions());
            String          hash     = org.springframework.util.DigestUtils.md5DigestAsHex(fileBytes);

            SummaryResponse summary  = summaryBuilder.build(enriched, parsed);
            Long            uploadId = persistenceGateway.save(hash, originalFilename, parsed, enriched);
            summary = summary.toBuilder().uploadId(uploadId).build();

            final SummaryResponse finalSummary = summary;
            jobs.computeIfPresent(jobId, (id, e) -> e.toDone(finalSummary));
            log.info("Job {} completed — bank={}, txns={}", jobId, parsed.getBankName(), enriched.size());
        } catch (Exception ex) {
            log.error("Job {} failed: {}", jobId, ex.getMessage(), ex);
            jobs.computeIfPresent(jobId, (id, e) -> e.toFailed(ex.getMessage()));
        }
    }

    // ── Status query ──────────────────────────────────────────────────────────

    public JobStatusResponse getStatus(String jobId) {
        JobEntry entry = jobs.get(jobId);
        if (entry == null) {
            return JobStatusResponse.builder()
                .jobId(jobId)
                .status(JobStatus.FAILED)
                .error("Job not found — it may have expired or never existed.")
                .build();
        }
        return JobStatusResponse.builder()
            .jobId(jobId)
            .status(entry.status())
            .result(entry.result())
            .error(entry.error())
            .submittedAt(ISO.format(entry.submittedAt()))
            .completedAt(entry.completedAt() != null ? ISO.format(entry.completedAt()) : null)
            .build();
    }

    // ── Scheduled cleanup ─────────────────────────────────────────────────────

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minusSeconds(JOB_TTL_SECONDS);
        int before = jobs.size();
        jobs.entrySet().removeIf(e -> e.getValue().submittedAt().isBefore(cutoff));
        int removed = before - jobs.size();
        if (removed > 0) log.info("Cleaned up {} expired jobs", removed);
    }

    // ── Internal job entry record ─────────────────────────────────────────────

    record JobEntry(
        JobStatus     status,
        SummaryResponse result,
        String        error,
        Instant       submittedAt,
        Instant       completedAt
    ) {
        static JobEntry pending(String filename) {
            return new JobEntry(JobStatus.PENDING, null, null, Instant.now(), null);
        }

        JobEntry toProcessing() {
            return new JobEntry(JobStatus.PROCESSING, null, null, submittedAt, null);
        }

        JobEntry toDone(SummaryResponse r) {
            return new JobEntry(JobStatus.DONE, r, null, submittedAt, Instant.now());
        }

        JobEntry toFailed(String err) {
            return new JobEntry(JobStatus.FAILED, null, err, submittedAt, Instant.now());
        }
    }
}

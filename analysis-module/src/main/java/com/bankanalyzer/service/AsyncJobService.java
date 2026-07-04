package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.JobStatusResponse;
import com.bankanalyzer.api.dto.SubmitJobResponse;
import com.bankanalyzer.api.dto.SummaryResponse;
import com.bankanalyzer.kafka.AnalysisJobEvent;
import com.bankanalyzer.kafka.AnalysisJobProducer;
import com.bankanalyzer.model.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncJobService {

    private static final long JOB_TTL_SECONDS = 3600;
    private static final DateTimeFormatter ISO = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final TempFileStore tempFileStore;
    private final AnalysisJobProducer producer;

    private final ConcurrentHashMap<String, JobEntry> jobs = new ConcurrentHashMap<>();

    // ── Submit: store bytes, publish job event to Kafka ──────────────────────

    public SubmitJobResponse submit(byte[] fileBytes, String originalFilename, String baseUrl) {
        String jobId = UUID.randomUUID().toString();

        tempFileStore.put(jobId, fileBytes);
        jobs.put(jobId, JobEntry.pending());

        producer.publishJob(new AnalysisJobEvent(jobId, originalFilename, baseUrl));
        log.info("Job {} submitted to Kafka (file: {})", jobId, originalFilename);

        return SubmitJobResponse.builder()
                .jobId(jobId)
                .statusUrl(baseUrl + "/api/analyze/status/" + jobId)
                .message("Job accepted. Poll statusUrl for results.")
                .build();
    }

    // ── Status callbacks from AnalysisResultListener ─────────────────────────

    public void markDone(String jobId, SummaryResponse result) {
        jobs.computeIfPresent(jobId, (id, e) -> e.toDone(result));
    }

    public void markFailed(String jobId, String error) {
        jobs.computeIfPresent(jobId, (id, e) -> e.toFailed(error));
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

    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minusSeconds(JOB_TTL_SECONDS);
        int before = jobs.size();
        jobs.entrySet().removeIf(e -> e.getValue().submittedAt().isBefore(cutoff));
        int removed = before - jobs.size();
        if (removed > 0) log.info("Cleaned up {} expired jobs", removed);
    }

    // ── Internal job entry ────────────────────────────────────────────────────

    record JobEntry(JobStatus status, SummaryResponse result, String error,
                    Instant submittedAt, Instant completedAt) {

        static JobEntry pending () {
            return new JobEntry(JobStatus.PENDING, null, null, Instant.now(), null);
        }

        JobEntry toDone (SummaryResponse r){
            return new JobEntry(JobStatus.DONE, r, null, submittedAt, Instant.now());
        }

        JobEntry toFailed (String err){
            return new JobEntry(JobStatus.FAILED, null, err, submittedAt, Instant.now());
        }
    }
}

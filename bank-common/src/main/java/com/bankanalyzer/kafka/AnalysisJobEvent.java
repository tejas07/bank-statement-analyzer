package com.bankanalyzer.kafka;

/**
 * Kafka message published to analysis-jobs topic.
 * File bytes are NOT included — consumer fetches them from the temp store via jobId.
 */
public record AnalysisJobEvent(
        String jobId,
        String originalFilename,
        String baseUrl
) {
}

package com.bankanalyzer.kafka;

import com.bankanalyzer.api.dto.SummaryResponse;

/**
 * Kafka message published to analysis-results topic after processing completes.
 */
public record AnalysisResultEvent(
        String jobId,
        boolean success,
        SummaryResponse result,
        String errorMessage
) {
}

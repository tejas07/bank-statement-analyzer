package com.bankanalyzer.kafka;

import com.bankanalyzer.api.dto.SummaryResponse;
import com.bankanalyzer.config.KafkaConfig;
import com.bankanalyzer.pipeline.StatementAnalysisPipeline;
import com.bankanalyzer.service.TempFileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisJobConsumer {

    private final StatementAnalysisPipeline pipeline;
    private final TempFileStore tempFileStore;
    private final AnalysisJobProducer producer;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_ANALYSIS_JOBS,
            groupId = "analysis-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload AnalysisJobEvent event) {
        String jobId = event.jobId();
        log.info("Kafka consumer picked up job {} (file: {})", jobId, event.originalFilename());

        byte[] fileBytes = tempFileStore.get(jobId);
        if (fileBytes == null) {
            log.error("No file bytes found for job {} — may have expired", jobId);
            producer.publishResult(new AnalysisResultEvent(jobId, false, null,
                    "File data expired before processing. Please resubmit."));
            return;
        }

        try {
            SummaryResponse summary = pipeline.analyzeAndPersist(fileBytes, event.originalFilename());

            log.info("Job {} processed — bank={}, txns={}",
                    jobId, summary.getDetectedBank(), summary.getTotalTransactions());
            producer.publishResult(new AnalysisResultEvent(jobId, true, summary, null));
        } catch (Exception ex) {
            log.error("Job {} failed during processing: {}", jobId, ex.getMessage(), ex);
            producer.publishResult(new AnalysisResultEvent(jobId, false, null, ex.getMessage()));
        } finally {
            tempFileStore.remove(jobId);
        }
    }
}

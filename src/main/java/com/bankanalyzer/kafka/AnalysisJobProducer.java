package com.bankanalyzer.kafka;

import com.bankanalyzer.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisJobProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishJob(AnalysisJobEvent event) {
        kafkaTemplate.send(KafkaConfig.TOPIC_ANALYSIS_JOBS, event.jobId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish job {} to Kafka: {}", event.jobId(), ex.getMessage());
                } else {
                    log.info("Job {} published to Kafka partition {}",
                        event.jobId(), result.getRecordMetadata().partition());
                }
            });
    }

    public void publishResult(AnalysisResultEvent event) {
        kafkaTemplate.send(KafkaConfig.TOPIC_ANALYSIS_RESULTS, event.jobId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish result for job {}: {}", event.jobId(), ex.getMessage());
                } else {
                    log.debug("Result for job {} published to Kafka", event.jobId());
                }
            });
    }
}

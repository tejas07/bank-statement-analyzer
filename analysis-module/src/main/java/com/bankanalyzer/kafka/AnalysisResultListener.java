package com.bankanalyzer.kafka;

import com.bankanalyzer.config.KafkaConfig;
import com.bankanalyzer.service.AsyncJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisResultListener {

    private final AsyncJobService asyncJobService;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_ANALYSIS_RESULTS,
            groupId = "result-listener-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onResult(@Payload AnalysisResultEvent event) {
        log.info("Result received for job {} — success={}", event.jobId(), event.success());
        if (event.success()) {
            asyncJobService.markDone(event.jobId(), event.result());
        } else {
            asyncJobService.markFailed(event.jobId(), event.errorMessage());
        }
    }
}

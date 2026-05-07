package com.bankanalyzer.kafka;

import com.bankanalyzer.analyzer.TransactionAnalyzer;
import com.bankanalyzer.api.dto.SummaryResponse;
import com.bankanalyzer.config.KafkaConfig;
import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.parser.BankStatementParser;
import com.bankanalyzer.service.PersistenceGateway;
import com.bankanalyzer.service.SummaryBuilder;
import com.bankanalyzer.service.TempFileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisJobConsumer {

    private final BankStatementParser  parser;
    private final TransactionAnalyzer  analyzer;
    private final SummaryBuilder       summaryBuilder;
    private final PersistenceGateway   persistenceGateway;
    private final TempFileStore        tempFileStore;
    private final AnalysisJobProducer  producer;

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
            ParseResult       parsed   = parser.parseWithMeta(new ByteArrayInputStream(fileBytes));
            List<Transaction> enriched = analyzer.analyze(parsed.getTransactions());
            String            hash     = DigestUtils.md5DigestAsHex(fileBytes);
            SummaryResponse   summary  = summaryBuilder.build(enriched, parsed);
            Long              uploadId = persistenceGateway.save(hash, event.originalFilename(), parsed, enriched);
            summary = summary.toBuilder().uploadId(uploadId).build();

            log.info("Job {} processed — bank={}, txns={}", jobId, parsed.getBankName(), enriched.size());
            producer.publishResult(new AnalysisResultEvent(jobId, true, summary, null));
        } catch (Exception ex) {
            log.error("Job {} failed during processing: {}", jobId, ex.getMessage(), ex);
            producer.publishResult(new AnalysisResultEvent(jobId, false, null, ex.getMessage()));
        } finally {
            tempFileStore.remove(jobId);
        }
    }
}

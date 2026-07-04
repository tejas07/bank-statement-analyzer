package com.bankanalyzer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_ANALYSIS_JOBS = "analysis-jobs";
    public static final String TOPIC_ANALYSIS_RESULTS = "analysis-results";

    @Bean
    public NewTopic analysisJobsTopic() {
        return TopicBuilder.name(TOPIC_ANALYSIS_JOBS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic analysisResultsTopic() {
        return TopicBuilder.name(TOPIC_ANALYSIS_RESULTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

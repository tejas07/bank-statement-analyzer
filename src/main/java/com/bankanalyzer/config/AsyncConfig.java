package com.bankanalyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("webhook-");
        executor.initialize();
        return executor;
    }

    /**
     * Shared reactive HTTP client used by {@link com.bankanalyzer.service.WebhookService}.
     * Response body buffer capped at 1 MB — webhook payloads are never large.
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
    }
}

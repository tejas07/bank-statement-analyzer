package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.SummaryResponse;
import com.bankanalyzer.validation.WebhookUrlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Delivers webhook notifications via a non-blocking {@link WebClient}.
 *
 * <p>Uses Reactor's {@code retryWhen} with fixed 1-second delays between attempts —
 * no {@link Thread#sleep} needed. The subscription is fire-and-forget; failures are
 * logged but never bubble up to the caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final WebClient webClient;
    private final WebhookUrlValidator webhookUrlValidator;

    /**
     * POSTs the summary result to the caller-provided webhook URL.
     * Runs on the {@code webhookExecutor} thread pool so it never blocks the HTTP response.
     * Retries up to {@value MAX_RETRIES} times on failure.
     */
    @Async("webhookExecutor")
    public void notify(String webhookUrl, SummaryResponse payload) {
        try {
            webhookUrlValidator.validate(webhookUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Webhook skipped — invalid URL '{}': {}", webhookUrl, e.getMessage());
            return;
        }

        webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .retryWhen(Retry.fixedDelay(MAX_RETRIES - 1, RETRY_DELAY)
                        .doBeforeRetry(signal -> log.warn(
                                "Webhook retry {}/{} for {} — previous error: {}",
                                signal.totalRetries() + 1, MAX_RETRIES,
                                webhookUrl, signal.failure().getMessage())))
                .subscribe(
                        response -> log.info("Webhook delivered to {} — HTTP {}",
                                webhookUrl, response.getStatusCode()),
                        error -> log.error("Webhook delivery failed after {} attempts for {}: {}",
                                MAX_RETRIES, webhookUrl, error.getMessage())
                );
    }
}

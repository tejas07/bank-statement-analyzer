package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.SummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Set;

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

    private static final int     MAX_RETRIES    = 3;
    private static final Duration RETRY_DELAY   = Duration.ofSeconds(1);
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "0.0.0.0", "::1");

    private final WebClient webClient;

    /**
     * POSTs the summary result to the caller-provided webhook URL.
     * Runs on the {@code webhookExecutor} thread pool so it never blocks the HTTP response.
     * Retries up to {@value MAX_RETRIES} times on failure.
     */
    @Async("webhookExecutor")
    public void notify(String webhookUrl, SummaryResponse payload) {
        try {
            validateUrl(webhookUrl);
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

    /**
     * Basic SSRF guard: must be http/https and must not target private/loopback addresses.
     */
    private void validateUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URL");
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only http/https allowed");
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (BLOCKED_HOSTS.contains(host)
                || host.startsWith("127.")
                || host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.matches("172\\.(1[6-9]|2\\d|3[01])\\..*")) {
            throw new IllegalArgumentException("Internal/private addresses not allowed");
        }
    }
}

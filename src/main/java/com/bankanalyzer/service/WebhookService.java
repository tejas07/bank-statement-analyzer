package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.SummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final int MAX_RETRIES = 3;
    private static final Set<String> BLOCKED_HOSTS = Set.of(
        "localhost", "0.0.0.0", "::1"
    );

    private final RestTemplate restTemplate;

    /**
     * POSTs the summary result to the caller-provided webhook URL.
     * Runs asynchronously so it never blocks the HTTP response.
     * Retries up to 3 times on failure.
     */
    @Async("webhookExecutor")
    public void notify(String webhookUrl, SummaryResponse payload) {
        try {
            validateUrl(webhookUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Webhook skipped — invalid URL '{}': {}", webhookUrl, e.getMessage());
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<SummaryResponse> request = new HttpEntity<>(payload, headers);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                restTemplate.postForEntity(webhookUrl, request, String.class);
                log.info("Webhook delivered to {} (attempt {})", webhookUrl, attempt);
                return;
            } catch (Exception e) {
                log.warn("Webhook attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, webhookUrl, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                }
            }
        }
        log.error("Webhook delivery failed after {} attempts for {}", MAX_RETRIES, webhookUrl);
    }

    /**
     * Basic SSRF guard: must be http/https, must not target private/loopback addresses.
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

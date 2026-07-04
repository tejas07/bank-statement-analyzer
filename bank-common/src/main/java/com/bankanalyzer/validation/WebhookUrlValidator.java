package com.bankanalyzer.validation;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Basic SSRF guard: must be http/https and must not target private/loopback addresses.
 */
@Component
public class WebhookUrlValidator implements Validator<String> {

    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "0.0.0.0", "::1");

    @Override
    public void validate(String url) {
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

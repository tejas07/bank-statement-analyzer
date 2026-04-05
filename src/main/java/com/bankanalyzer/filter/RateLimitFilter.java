package com.bankanalyzer.filter;

import com.bankanalyzer.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final LoadingCache<String, Bucket> buckets;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(RateLimitProperties props) {
        this.props = props;
        this.buckets = Caffeine.newBuilder()
            .maximumSize(props.getCacheSize())
            .expireAfterAccess(props.getCacheExpireMinutes(), TimeUnit.MINUTES)
            .build(ip -> newBucket());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only rate-limit the analyze endpoints; health check is free
        String path = request.getRequestURI();
        return !path.startsWith("/api/analyze");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (!props.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        Bucket bucket = buckets.get(ip);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.addHeader("X-Rate-Limit-Capacity", String.valueOf(props.getCapacity()));
        response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
            response.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            log.warn("Rate limit exceeded for IP {} on {}", ip, request.getRequestURI());

            objectMapper.writeValue(response.getWriter(), Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "message", String.format(
                    "Rate limit exceeded. Max %d requests per %d %s. Retry after %d seconds.",
                    props.getCapacity(),
                    props.getRefillDuration(),
                    props.getRefillUnit().name().toLowerCase(),
                    retryAfterSeconds)
            ));
        }
    }

    private Bucket newBucket() {
        Duration period = Duration.of(props.getRefillDuration(),
            props.getRefillUnit().toChronoUnit());
        Bandwidth limit = Bandwidth.classic(
            props.getCapacity(),
            Refill.greedy(props.getRefillTokens(), period));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Resolves real client IP, accounting for reverse proxies (X-Forwarded-For).
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}

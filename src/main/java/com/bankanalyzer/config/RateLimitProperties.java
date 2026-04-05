package com.bankanalyzer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Data
@Component
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    /** Master switch — set to false to disable rate limiting entirely. */
    private boolean enabled = true;

    /** Maximum tokens the bucket can hold (controls burst size). */
    private int capacity = 20;

    /** Number of tokens added per refill period. */
    private int refillTokens = 20;

    /** Length of each refill period. */
    private long refillDuration = 1;

    /** Unit for refillDuration: SECONDS, MINUTES, HOURS. */
    private TimeUnit refillUnit = TimeUnit.MINUTES;

    /** Maximum number of unique IPs to track simultaneously. */
    private long cacheSize = 10_000;

    /** Evict an IP's bucket after this many minutes of inactivity. */
    private long cacheExpireMinutes = 10;
}

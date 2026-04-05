package com.bankanalyzer.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * paymentMode  — deterministic pure function; 1 000 entries, never expires.
     * merchant     — deterministic pure function; 1 000 entries, never expires.
     * analysis     — full PDF analysis result keyed by SHA-256; 30 entries, 1-hour TTL.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Register each cache with its own spec
        manager.registerCustomCache("paymentMode",
            Caffeine.newBuilder().maximumSize(1_000).build());

        manager.registerCustomCache("merchant",
            Caffeine.newBuilder().maximumSize(1_000).build());

        manager.registerCustomCache("analysis",
            Caffeine.newBuilder()
                .maximumSize(30)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build());

        manager.registerCustomCache("category",
            Caffeine.newBuilder().maximumSize(1_000).build());

        return manager;
    }
}

package com.bankanalyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds raw PDF bytes in memory between job submission and Kafka consumer pickup.
 * Entries auto-expire after 30 minutes to prevent memory leaks.
 */
@Slf4j
@Component
public class TempFileStore {

    private static final long TTL_SECONDS = 1800; // 30 minutes

    private record Entry(byte[] bytes, Instant storedAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public void put(String jobId, byte[] bytes) {
        store.put(jobId, new Entry(bytes, Instant.now()));
        log.debug("TempFileStore: stored {} bytes for job {}", bytes.length, jobId);
    }

    public byte[] get(String jobId) {
        Entry entry = store.get(jobId);
        return entry != null ? entry.bytes() : null;
    }

    public void remove(String jobId) {
        store.remove(jobId);
        log.debug("TempFileStore: removed bytes for job {}", jobId);
    }

    @Scheduled(fixedRate = 300_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(TTL_SECONDS);
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue().storedAt().isBefore(cutoff));
        int removed = before - store.size();
        if (removed > 0) log.info("TempFileStore: evicted {} expired entries", removed);
    }
}

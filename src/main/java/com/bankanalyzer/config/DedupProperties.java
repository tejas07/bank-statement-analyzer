package com.bankanalyzer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dedup")
public class DedupProperties {

    /** Master switch — set to false to allow re-uploading the same file. */
    private boolean enabled = true;

    /**
     * How many hours back to look when checking for a duplicate upload.
     * Uploads older than this window are not considered duplicates.
     */
    private int windowHours = 24;
}

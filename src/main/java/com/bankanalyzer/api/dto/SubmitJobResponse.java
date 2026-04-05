package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubmitJobResponse {
    private final String jobId;
    private final String statusUrl;
    private final String message;
}

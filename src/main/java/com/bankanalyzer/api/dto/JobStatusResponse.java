package com.bankanalyzer.api.dto;

import com.bankanalyzer.model.JobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatusResponse {
    private final String        jobId;
    private final JobStatus     status;
    private final SummaryResponse result;
    private final String        error;
    private final String        submittedAt;
    private final String        completedAt;
}

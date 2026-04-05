package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonthSummary {
    private final String month;
    private final long debitCount;
    private final double totalDebit;
    private final long creditCount;
    private final double totalCredit;
}

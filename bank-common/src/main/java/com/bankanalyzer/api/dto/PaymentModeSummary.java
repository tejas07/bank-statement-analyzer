package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentModeSummary {
    private final String mode;
    private final int count;
    private final double totalDebit;
    private final double totalCredit;
}

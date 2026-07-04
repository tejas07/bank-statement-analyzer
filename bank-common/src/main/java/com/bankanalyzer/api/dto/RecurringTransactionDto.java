package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecurringTransactionDto {
    private final String merchantName;
    private final int occurrences;
    private final double averageAmount;
    private final double totalAmount;
}

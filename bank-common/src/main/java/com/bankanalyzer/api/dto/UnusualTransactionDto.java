package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnusualTransactionDto {
    private final String date;
    private final String description;
    private final String merchantName;
    private final double amount;
}

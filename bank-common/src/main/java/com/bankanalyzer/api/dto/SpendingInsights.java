package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SpendingInsights {
    private final String highestSpendDay;           // "2024-03-15"
    private final double highestSpendDayAmount;
    private final String highestSpendMonth;         // "2024-03"
    private final double highestSpendMonthAmount;
    private final double averageMonthlySpend;
    private final List<RecurringTransactionDto> recurringTransactions;
    private final List<UnusualTransactionDto> unusualTransactions;
}

package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.RecurringTransactionDto;
import com.bankanalyzer.api.dto.SpendingInsights;
import com.bankanalyzer.api.dto.UnusualTransactionDto;
import com.bankanalyzer.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InsightService {

    private static final int    MIN_RECURRENCE    = 2;     // minimum occurrences to be "recurring"
    private static final double UNUSUAL_STD_DEVS  = 2.0;   // threshold for unusual transactions
    private static final int    MAX_UNUSUAL        = 10;

    public SpendingInsights compute(List<Transaction> transactions) {
        List<Transaction> debits = transactions.stream()
            .filter(Transaction::isDebit)
            .collect(Collectors.toList());

        if (debits.isEmpty()) {
            return SpendingInsights.builder()
                .recurringTransactions(List.of())
                .unusualTransactions(List.of())
                .build();
        }

        return SpendingInsights.builder()
            .highestSpendDay(highestSpendDay(debits))
            .highestSpendDayAmount(highestSpendDayAmount(debits))
            .highestSpendMonth(highestSpendMonth(debits))
            .highestSpendMonthAmount(highestSpendMonthAmount(debits))
            .averageMonthlySpend(averageMonthlySpend(debits))
            .recurringTransactions(recurringTransactions(debits))
            .unusualTransactions(unusualTransactions(debits))
            .build();
    }

    // ── Highest spend day ────────────────────────────────────────────────────

    private String highestSpendDay(List<Transaction> debits) {
        return debits.stream()
            .collect(Collectors.groupingBy(t -> t.getDate().toString(),
                Collectors.summingDouble(Transaction::getDebit)))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private double highestSpendDayAmount(List<Transaction> debits) {
        return debits.stream()
            .collect(Collectors.groupingBy(t -> t.getDate().toString(),
                Collectors.summingDouble(Transaction::getDebit)))
            .values().stream()
            .mapToDouble(Double::doubleValue)
            .max().orElse(0);
    }

    // ── Highest spend month ──────────────────────────────────────────────────

    private String highestSpendMonth(List<Transaction> debits) {
        return debits.stream()
            .collect(Collectors.groupingBy(Transaction::getMonthKey,
                Collectors.summingDouble(Transaction::getDebit)))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private double highestSpendMonthAmount(List<Transaction> debits) {
        return debits.stream()
            .collect(Collectors.groupingBy(Transaction::getMonthKey,
                Collectors.summingDouble(Transaction::getDebit)))
            .values().stream()
            .mapToDouble(Double::doubleValue)
            .max().orElse(0);
    }

    // ── Average monthly spend ────────────────────────────────────────────────

    private double averageMonthlySpend(List<Transaction> debits) {
        Map<String, Double> byMonth = debits.stream()
            .collect(Collectors.groupingBy(Transaction::getMonthKey,
                Collectors.summingDouble(Transaction::getDebit)));
        if (byMonth.isEmpty()) return 0;
        return byMonth.values().stream().mapToDouble(Double::doubleValue).sum() / byMonth.size();
    }

    // ── Recurring transactions ───────────────────────────────────────────────

    private List<RecurringTransactionDto> recurringTransactions(List<Transaction> debits) {
        return debits.stream()
            .collect(Collectors.groupingBy(Transaction::getMerchantName))
            .entrySet().stream()
            .filter(e -> e.getValue().size() >= MIN_RECURRENCE)
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .map(e -> {
                List<Transaction> group = e.getValue();
                double avg = group.stream().mapToDouble(Transaction::getDebit).average().orElse(0);
                double total = group.stream().mapToDouble(Transaction::getDebit).sum();
                return RecurringTransactionDto.builder()
                    .merchantName(e.getKey())
                    .occurrences(group.size())
                    .averageAmount(Math.round(avg * 100.0) / 100.0)
                    .totalAmount(Math.round(total * 100.0) / 100.0)
                    .build();
            })
            .collect(Collectors.toList());
    }

    // ── Unusual (high-value) transactions ────────────────────────────────────

    private List<UnusualTransactionDto> unusualTransactions(List<Transaction> debits) {
        double[] amounts = debits.stream().mapToDouble(Transaction::getDebit).toArray();
        double mean   = Arrays.stream(amounts).average().orElse(0);
        double stdDev = stdDev(amounts, mean);
        double threshold = mean + UNUSUAL_STD_DEVS * stdDev;

        return debits.stream()
            .filter(t -> t.getDebit() > threshold)
            .sorted((a, b) -> Double.compare(b.getDebit(), a.getDebit()))
            .limit(MAX_UNUSUAL)
            .map(t -> UnusualTransactionDto.builder()
                .date(t.getDate().toString())
                .description(t.getDescription())
                .merchantName(t.getMerchantName())
                .amount(t.getDebit())
                .build())
            .collect(Collectors.toList());
    }

    private double stdDev(double[] values, double mean) {
        if (values.length < 2) return 0;
        double variance = Arrays.stream(values)
            .map(v -> (v - mean) * (v - mean))
            .average().orElse(0);
        return Math.sqrt(variance);
    }
}

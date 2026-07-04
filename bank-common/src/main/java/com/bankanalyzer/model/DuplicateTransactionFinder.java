package com.bankanalyzer.model;

import com.bankanalyzer.api.dto.DuplicateGroup;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure, framework-free duplicate-transaction detection — shared by
 * transaction-analysis and report-generation, kept in the domain (bank-common)
 * layer so both can depend on it without depending on each other.
 *
 * <p>Two transactions are considered duplicates when they share the same:
 * - Normalized description (trimmed, lower-cased, whitespace collapsed)
 * - Debit amount (rounded to 2 decimal places)
 * - Credit amount (rounded to 2 decimal places)
 */
public final class DuplicateTransactionFinder {

    private DuplicateTransactionFinder() {
    }

    public static List<DuplicateGroup> find(List<Transaction> transactions) {
        Map<String, List<Transaction>> groups = transactions.stream()
                .collect(Collectors.groupingBy(DuplicateTransactionFinder::dedupeKey));

        return groups.values().stream()
                .filter(g -> g.size() > 1)
                .sorted((a, b) -> Integer.compare(b.size(), a.size()))
                .map(g -> {
                    Transaction sample = g.get(0);
                    List<String> dates = g.stream()
                            .map(t -> t.getDate() != null ? t.getDate().toString() : "unknown")
                            .sorted()
                            .collect(Collectors.toList());
                    return DuplicateGroup.builder()
                            .description(sample.getDescription())
                            .debit(round2(sample.getDebit()))
                            .credit(round2(sample.getCredit()))
                            .count(g.size())
                            .occurrenceDates(dates)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static String dedupeKey(Transaction t) {
        String normalizedDesc = t.getDescription() == null ? ""
                : t.getDescription().trim().toLowerCase().replaceAll("\\s+", " ");
        return normalizedDesc + "|" + round2(t.getDebit()) + "|" + round2(t.getCredit());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.DuplicateGroup;
import com.bankanalyzer.model.Transaction;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Feature 3: Detects duplicate transactions within a statement.
 *
 * Two transactions are considered duplicates when they share the same:
 *   - Normalized description (trimmed, lower-cased, whitespace collapsed)
 *   - Debit amount (rounded to 2 decimal places)
 *   - Credit amount (rounded to 2 decimal places)
 *
 * Transactions on different dates still count as duplicates because
 * repeated charges (e.g., double-billing) can occur across days.
 */
@Service
public class DuplicateDetector {

    public List<DuplicateGroup> detect(List<Transaction> transactions) {
        Map<String, List<Transaction>> groups = transactions.stream()
            .collect(Collectors.groupingBy(this::dedupeKey));

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

    private String dedupeKey(Transaction t) {
        String normalizedDesc = t.getDescription() == null ? ""
            : t.getDescription().trim().toLowerCase().replaceAll("\\s+", " ");
        return normalizedDesc + "|" + round2(t.getDebit()) + "|" + round2(t.getCredit());
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

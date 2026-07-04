package com.bankanalyzer.service.analytics;

import com.bankanalyzer.model.Transaction;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Small rounding/filtering helpers shared by two or more analytics classes.
 */
public final class TransactionMath {

    private TransactionMath() {
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static List<Transaction> debits(List<Transaction> txns) {
        return txns.stream().filter(Transaction::isDebit).collect(Collectors.toList());
    }

    public static int distinctMonths(List<Transaction> debits) {
        return (int) debits.stream()
                .filter(t -> t.getDate() != null)
                .map(Transaction::getMonthKey)
                .distinct().count();
    }
}

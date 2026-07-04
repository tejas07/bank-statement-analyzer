package com.bankanalyzer.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Pure, framework-free grouping/aggregation over {@link Transaction} lists —
 * shared by transaction-analysis and report-generation, kept in the domain
 * (bank-common) layer so both can depend on it without depending on each other.
 */
public final class TransactionGroups {

    private TransactionGroups() {
    }

    public static Map<PaymentMode, List<Transaction>> groupByPaymentMode(List<Transaction> txns) {
        Map<PaymentMode, List<Transaction>> map = new LinkedHashMap<>();
        for (PaymentMode mode : PaymentMode.values()) {
            map.put(mode, new ArrayList<>());
        }
        for (Transaction t : txns) {
            map.get(t.getPaymentMode()).add(t);
        }
        // Remove empty modes
        map.entrySet().removeIf(e -> e.getValue().isEmpty());
        return map;
    }

    /**
     * Groups debit transactions by merchant, sorted by total spend descending.
     */
    public static Map<String, List<Transaction>> groupByMerchant(List<Transaction> txns) {
        Map<String, List<Transaction>> map = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (t.isDebit()) {
                map.computeIfAbsent(t.getMerchantName(), k -> new ArrayList<>()).add(t);
            }
        }
        // Sort by total debit descending
        return map.entrySet().stream()
                .sorted((a, b) -> Double.compare(totalDebit(b.getValue()), totalDebit(a.getValue())))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public static double totalDebit(List<Transaction> txns) {
        return txns.stream().mapToDouble(Transaction::getDebit).sum();
    }

    /**
     * Groups transactions by YearMonth ("yyyy-MM"), in chronological order.
     */
    public static TreeMap<String, List<Transaction>> groupByMonth(List<Transaction> txns) {
        TreeMap<String, List<Transaction>> map = new TreeMap<>();
        for (Transaction t : txns) {
            map.computeIfAbsent(t.getMonthKey(), k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    public static double totalCredit(List<Transaction> txns) {
        return txns.stream().mapToDouble(Transaction::getCredit).sum();
    }
}

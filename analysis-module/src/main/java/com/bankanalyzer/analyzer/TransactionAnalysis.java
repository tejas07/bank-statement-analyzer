package com.bankanalyzer.analyzer;

import com.bankanalyzer.model.PaymentMode;
import com.bankanalyzer.model.Transaction;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Abstraction over transaction enrichment and grouping — lets callers (controllers,
 * report generators, the analysis pipeline) depend on this contract rather than the
 * concrete {@link TransactionAnalyzer}, which becomes a mechanical swap once this
 * logic is extracted into its own service (see the microservices extraction plan).
 */
public interface TransactionAnalysis {

    List<Transaction> analyze(List<Transaction> raw);

    PaymentMode detectPaymentMode(String description);

    String extractMerchant(String description, PaymentMode mode);

    Map<PaymentMode, List<Transaction>> groupByPaymentMode(List<Transaction> txns);

    Map<String, List<Transaction>> groupByMerchant(List<Transaction> txns);

    TreeMap<String, List<Transaction>> groupByMonth(List<Transaction> txns);

    double totalDebit(List<Transaction> txns);

    double totalCredit(List<Transaction> txns);
}

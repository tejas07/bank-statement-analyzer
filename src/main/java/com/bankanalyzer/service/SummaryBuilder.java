package com.bankanalyzer.service;

import com.bankanalyzer.analyzer.TransactionAnalyzer;
import com.bankanalyzer.api.dto.*;
import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.PaymentMode;
import com.bankanalyzer.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Extracted from AnalyzeController so that both the synchronous and
 * async (Feature 8) code paths can reuse the same summary-building logic.
 */
@Service
@RequiredArgsConstructor
public class SummaryBuilder {

    private static final int TOP_MERCHANTS = 20;

    private final TransactionAnalyzer analyzer;
    private final InsightService      insightService;
    private final DuplicateDetector   duplicateDetector;

    public SummaryResponse build(List<Transaction> transactions, ParseResult parsed) {
        return build(transactions, parsed, parsed.getBankName());
    }

    public SummaryResponse build(List<Transaction> transactions, ParseResult parsed,
                                 String bankNameOverride) {
        Map<PaymentMode, List<Transaction>>     byMode     = analyzer.groupByPaymentMode(transactions);
        Map<String, List<Transaction>>          byMerchant = analyzer.groupByMerchant(transactions);
        TreeMap<String, List<Transaction>>      byMonth    = analyzer.groupByMonth(transactions);
        SpendingInsights                        insights   = insightService.compute(transactions);
        List<DuplicateGroup>                    duplicates = duplicateDetector.detect(transactions);

        List<PaymentModeSummary> modeSummaries = byMode.entrySet().stream()
            .map(e -> PaymentModeSummary.builder()
                .mode(e.getKey().getLabel())
                .count(e.getValue().size())
                .totalDebit(analyzer.totalDebit(e.getValue()))
                .totalCredit(analyzer.totalCredit(e.getValue()))
                .build())
            .collect(Collectors.toList());

        List<MerchantSummary> merchantSummaries = byMerchant.entrySet().stream()
            .limit(TOP_MERCHANTS)
            .map(e -> MerchantSummary.builder()
                .merchant(e.getKey())
                .count(e.getValue().size())
                .totalDebit(analyzer.totalDebit(e.getValue()))
                .build())
            .collect(Collectors.toList());

        List<MonthSummary> monthSummaries = byMonth.entrySet().stream()
            .map(e -> {
                List<Transaction> txns = e.getValue();
                return MonthSummary.builder()
                    .month(e.getKey())
                    .debitCount(txns.stream().filter(Transaction::isDebit).count())
                    .totalDebit(analyzer.totalDebit(txns))
                    .creditCount(txns.stream().filter(Transaction::isCredit).count())
                    .totalCredit(analyzer.totalCredit(txns))
                    .build();
            })
            .collect(Collectors.toList());

        return SummaryResponse.builder()
            .detectedBank(bankNameOverride)
            .statementType(parsed.getStatementType().name())
            .totalTransactions(transactions.size())
            .totalDebit(analyzer.totalDebit(transactions))
            .totalCredit(analyzer.totalCredit(transactions))
            .byPaymentMode(modeSummaries)
            .byMerchant(merchantSummaries)
            .byMonth(monthSummaries)
            .insights(insights)
            .duplicates(duplicates.isEmpty() ? null : duplicates)
            .customerDetails(parsed.getCustomerDetails())
            .build();
    }
}

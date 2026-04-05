package com.bankanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SummaryResponse {
    private final Long   uploadId;
    /** Single-file: detected bank name. Multi-file: first detected bank (see detectedBanks). */
    private final String detectedBank;
    /** Multi-file: all detected bank names (one per uploaded file). */
    private final List<String>             detectedBanks;
    private final String statementType;
    private final int    totalTransactions;
    private final double totalDebit;
    private final double totalCredit;
    private final List<PaymentModeSummary> byPaymentMode;
    private final List<MerchantSummary>    byMerchant;
    private final List<MonthSummary>       byMonth;
    private final SpendingInsights         insights;
    /** Feature 3: groups of possible duplicate transactions (null when none found). */
    private final List<DuplicateGroup>     duplicates;
    /** Customer and account metadata extracted from the PDF header. */
    private final CustomerDetails          customerDetails;
}

package com.bankanalyzer.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * Immutable value object representing a single bank transaction.
 */
@Getter
@Builder
@ToString
public final class Transaction {

    private final LocalDate date;
    private final String description;

    @Builder.Default
    private final double debit = 0.0;

    @Builder.Default
    private final double credit = 0.0;

    private final double balance;

    @Builder.Default
    private final PaymentMode paymentMode = PaymentMode.OTHER;

    @Builder.Default
    private final String merchantName = "Unknown";

    @Builder.Default
    private final Category category = Category.OTHER;

    /** True if money went out of the account. */
    public boolean isDebit()  { return debit > 0.0; }

    /** True if money came into the account. */
    public boolean isCredit() { return credit > 0.0; }

    /** Returns a "yyyy-MM" key for monthly grouping, e.g. "2024-03". */
    public String getMonthKey() {
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }
}

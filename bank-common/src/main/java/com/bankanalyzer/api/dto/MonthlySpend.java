package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Actual spend for a single calendar month within a category.
 */
@Getter
@Builder
public class MonthlySpend {

    /**
     * Month key in "yyyy-MM" format, e.g. "2024-03".
     */
    private final String month;

    /**
     * Total debited in this category during the month.
     */
    private final double amount;

    /**
     * Percentage change versus the previous month.
     * 0.0 for the first month in the series (no prior baseline).
     */
    private final double changeFromPrevious;
}

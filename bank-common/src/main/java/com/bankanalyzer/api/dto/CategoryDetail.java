package com.bankanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Stats for a single spending category (or group of categories).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryDetail {

    /**
     * Display name, e.g. "Food & Groceries".
     */
    private final String categoryName;

    /**
     * Underlying Category enum names that roll up into this group.
     */
    private final List<String> subCategories;

    /**
     * Total debited in this category across the full statement period.
     */
    private final double totalSpend;

    /**
     * totalSpend as a percentage of overall total spend (0–100).
     */
    private final double percentageOfTotal;

    /**
     * totalSpend / number of months in the statement.
     */
    private final double averageMonthlySpend;

    /**
     * Month-over-month percentage change comparing the last full month
     * to the month before it. Positive = spending increased.
     */
    private final double momChangePercent;

    /**
     * INCREASING / STABLE / DECREASING based on linear regression slope.
     */
    private final String trendDirection;

    /**
     * Chronological list of monthly totals.
     */
    private final List<MonthlySpend> monthlyBreakdown;

    /**
     * Top merchants (by debit amount) in this category, up to 5.
     */
    private final List<MerchantSummary> topMerchants;

    /**
     * Highest single-month spend in this category.
     */
    private final double highestSpend;

    /**
     * Month (yyyy-MM) of highestSpend.
     */
    private final String highestSpendMonth;
}

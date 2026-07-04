package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * A single actionable recommendation to improve spending productivity.
 */
@Getter
@Builder
public class SpendingRecommendation {

    /**
     * Rank: 1 = highest potential impact.
     */
    private final int priority;

    /**
     * Spending category this recommendation targets.
     */
    private final String category;

    /**
     * Suggested action: REDUCE / REVIEW / MAINTAIN / INCREASE.
     */
    private final String action;

    /**
     * Human-readable explanation and suggested steps.
     */
    private final String message;

    /**
     * Current average monthly amount in this category.
     */
    private final double currentMonthlyAmount;

    /**
     * Suggested monthly target amount.
     */
    private final double targetMonthlyAmount;

    /**
     * currentMonthlyAmount − targetMonthlyAmount.
     */
    private final double potentialMonthlySavings;

    /**
     * potentialMonthlySavings × 12.
     */
    private final double annualSavingsPotential;
}

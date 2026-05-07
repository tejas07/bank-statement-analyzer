package com.bankanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Top-level response for POST /api/spending/productivity.
 *
 * <p>Combines a financial health score, 50/30/20 budget analysis,
 * essential vs discretionary split, and ranked spending recommendations
 * to help the user allocate money more productively.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductivityInsightsResponse {

    // ── Financial health ─────────────────────────────────────────────────────

    /** Composite score 0–100. Higher = healthier financial behaviour. */
    private final int financialHealthScore;

    /** EXCELLENT (≥80) / GOOD (≥60) / FAIR (≥40) / NEEDS_ATTENTION (<40). */
    private final String healthRating;

    // ── Income & savings summary ─────────────────────────────────────────────

    /** Total salary/income credits detected in the statement. */
    private final double totalIncome;

    /** Total debits (outgoing spend). */
    private final double totalSpend;

    /** totalIncome − totalSpend (can be negative). */
    private final double netSavings;

    /** (netSavings / totalIncome) × 100. 0 when income is unknown. */
    private final double savingsRate;

    // ── Budget rule ──────────────────────────────────────────────────────────

    private final BudgetRuleAnalysis budgetRuleAnalysis;

    // ── Essential vs discretionary ───────────────────────────────────────────

    /** Spend on needs: utilities, EMI, groceries, health, fuel. */
    private final double essentialSpend;

    /** Spend on wants: dining, entertainment, shopping, travel. */
    private final double discretionarySpend;

    private final double essentialPercent;
    private final double discretionaryPercent;

    // ── Category leaderboard ─────────────────────────────────────────────────

    /** All categories sorted by totalSpend descending. */
    private final List<CategoryDetail> topSpendingCategories;

    // ── Actionable recommendations ───────────────────────────────────────────

    /** Ranked list of actions, highest impact first. */
    private final List<SpendingRecommendation> recommendations;

    // ── Efficiency metrics ───────────────────────────────────────────────────

    /** totalSpend / number of days in the statement. */
    private final double averageDailySpend;

    /** averageDailySpend × 365. */
    private final double projectedAnnualSpend;

    /**
     * Rough emergency-fund indicator: netSavings / (totalSpend / months).
     * Represents how many months of spending the net savings cover.
     */
    private final double emergencyFundMonths;
}

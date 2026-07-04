package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 50/30/20 budget rule analysis for the statement period.
 *
 * <ul>
 *   <li><b>Needs  (50 %)</b> — Utilities, EMI/Loans, Groceries, Health, Fuel</li>
 *   <li><b>Wants  (30 %)</b> — Food/Dining, Entertainment, Shopping, Travel, Education</li>
 *   <li><b>Savings(20 %)</b> — Investments + net credit inflow above expenses</li>
 * </ul>
 */
@Getter
@Builder
public class BudgetRuleAnalysis {

    // ── Target percentages (rule benchmark) ─────────────────────────────────

    private final double needsTargetPercent;    // 50.0
    private final double wantsTargetPercent;    // 30.0
    private final double savingsTargetPercent;  // 20.0

    // ── Actual percentages ───────────────────────────────────────────────────

    private final double needsActualPercent;
    private final double wantsActualPercent;
    private final double savingsActualPercent;

    // ── Actual amounts (total over the statement period) ─────────────────────

    private final double needsAmount;
    private final double wantsAmount;
    private final double savingsAmount;

    // ── Compliance status for each bucket ────────────────────────────────────

    /**
     * ON_TARGET / OVER / UNDER relative to the 50/30/20 benchmark.
     */
    private final String needsStatus;
    private final String wantsStatus;
    private final String savingsStatus;

    // ── Category breakdown per bucket ────────────────────────────────────────

    private final List<String> needsCategories;
    private final List<String> wantsCategories;
    private final List<String> savingsCategories;

    /**
     * How much the user needs to redirect monthly to hit the 20 % savings target.
     * 0 if already on target or above.
     */
    private final double savingsGapMonthly;
}

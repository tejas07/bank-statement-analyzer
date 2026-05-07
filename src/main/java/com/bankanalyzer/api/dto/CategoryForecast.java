package com.bankanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Forecast for a single category or category group over the requested horizon.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryForecast {

    /** Display name matching the CategoryDetail it was derived from. */
    private final String categoryName;

    /** Mean monthly spend over the historical period (raw, pre-inflation). */
    private final double historicalMonthlyAverage;

    /**
     * Linear-regression slope in ₹/month (positive = upward trend).
     * Rounded to two decimal places.
     */
    private final double trendSlope;

    /** INCREASING / STABLE / DECREASING. */
    private final String trendDirection;

    /** Per-month projections for the requested forecast horizon. */
    private final List<MonthlyProjection> projections;

    // ── Horizon totals ───────────────────────────────────────────────────────

    /** Sum of conservative projections across the full horizon. */
    private final double conservativeTotal;

    /** Sum of baseline projections across the full horizon. */
    private final double baselineTotal;

    /** Sum of pessimistic projections across the full horizon. */
    private final double pessimisticTotal;

    /**
     * Potential savings if the user achieves the conservative scenario
     * instead of the baseline: {@code baselineTotal - conservativeTotal}.
     */
    private final double potentialSavings;
}

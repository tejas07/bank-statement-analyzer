package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Three-scenario spending projection for a single future month.
 */
@Getter
@Builder
public class MonthlyProjection {

    /** Future month key in "yyyy-MM" format. */
    private final String month;

    /**
     * Conservative (spending-control) scenario:
     * historical average reduced by 10 %, compounded by monthly inflation.
     */
    private final double conservative;

    /**
     * Baseline scenario:
     * historical average compounded by monthly inflation — "no behaviour change".
     */
    private final double baseline;

    /**
     * Pessimistic (trend-extrapolation) scenario:
     * linear-regression projection of past trend, then inflation-adjusted.
     */
    private final double pessimistic;
}

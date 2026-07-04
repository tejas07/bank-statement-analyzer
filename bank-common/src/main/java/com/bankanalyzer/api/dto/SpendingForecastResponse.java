package com.bankanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Top-level response for POST /api/spending/forecast.
 *
 * <p>Three projection scenarios are provided for every category:
 * <ul>
 *   <li><b>Conservative</b> — historical average −10 % + inflation (spending-control goal)</li>
 *   <li><b>Baseline</b>     — historical average + inflation (no behaviour change)</li>
 *   <li><b>Pessimistic</b>  — linear-regression trend extrapolation + inflation</li>
 * </ul>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpendingForecastResponse {

    /**
     * Annual inflation rate used in the projection (e.g. 6.0 = 6 %).
     */
    private final double annualInflationRate;

    /**
     * Number of future months projected.
     */
    private final int forecastMonths;

    /**
     * Forecast for Food & Groceries.
     */
    private final CategoryForecast food;

    /**
     * Forecast for Hotel & Merchant (retail/shopping) spend.
     */
    private final CategoryForecast hotelAndMerchant;

    /**
     * Forecast for Entertainment.
     */
    private final CategoryForecast entertainment;

    /**
     * Forecast for Travel & Fuel.
     */
    private final CategoryForecast travel;

    /**
     * Aggregate forecast across all spending categories.
     */
    private final CategoryForecast totalSpending;

    /**
     * Short description of the statistical approach used.
     */
    private final String methodology;

    /**
     * Key assumptions behind the projections.
     */
    private final List<String> assumptions;

    /**
     * Total potential savings over the forecast period if the user
     * hits the conservative target across all four focus categories.
     */
    private final double totalPotentialSavings;
}

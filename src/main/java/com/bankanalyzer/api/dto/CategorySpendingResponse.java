package com.bankanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Top-level response for POST /api/spending/categories.
 * Contains spending breakdown across the four focus groups and all raw categories.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategorySpendingResponse {

    /** Food & restaurant dining + grocery spend combined. */
    private final CategoryDetail food;

    /** Retail & merchant spend (shopping, e-commerce). */
    private final CategoryDetail hotelAndMerchant;

    /** Streaming, cinema, gaming, and leisure spend. */
    private final CategoryDetail entertainment;

    /** Flights, trains, cabs, and fuel combined. */
    private final CategoryDetail travel;

    /** Full breakdown for every detected category. */
    private final List<CategoryDetail> allCategories;

    /** Sum of all debit transactions in the statement. */
    private final double totalSpend;

    /** "yyyy-MM to yyyy-MM" date range of the statement. */
    private final String dateRange;

    /** Number of distinct calendar months in the statement. */
    private final int totalMonths;
}

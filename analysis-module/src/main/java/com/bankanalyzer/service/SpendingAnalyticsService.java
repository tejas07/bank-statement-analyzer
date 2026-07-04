package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.CategorySpendingResponse;
import com.bankanalyzer.api.dto.ProductivityInsightsResponse;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.service.analytics.CategorySpendingCalculator;
import com.bankanalyzer.service.analytics.ProductivityInsightsBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade over the {@code com.bankanalyzer.service.analytics} collaborators —
 * kept as the stable public entry point so {@link SpendingController} and
 * {@link ForecastService}'s siblings don't need to know about the internal split.
 */
@Service
@RequiredArgsConstructor
public class SpendingAnalyticsService {

    private final CategorySpendingCalculator categorySpendingCalculator;
    private final ProductivityInsightsBuilder productivityInsightsBuilder;

    public CategorySpendingResponse buildCategorySpending(List<Transaction> transactions) {
        return categorySpendingCalculator.buildCategorySpending(transactions);
    }

    public ProductivityInsightsResponse buildProductivityInsights(List<Transaction> transactions) {
        return productivityInsightsBuilder.build(transactions);
    }
}

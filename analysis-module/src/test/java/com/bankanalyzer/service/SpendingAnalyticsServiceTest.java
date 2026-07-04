package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.BudgetRuleAnalysis;
import com.bankanalyzer.api.dto.CategoryDetail;
import com.bankanalyzer.api.dto.CategorySpendingResponse;
import com.bankanalyzer.api.dto.ProductivityInsightsResponse;
import com.bankanalyzer.api.dto.SpendingRecommendation;
import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.service.analytics.BudgetRuleAnalyzer;
import com.bankanalyzer.service.analytics.CategoryLabelProvider;
import com.bankanalyzer.service.analytics.CategorySpendingCalculator;
import com.bankanalyzer.service.analytics.FinancialHealthScorer;
import com.bankanalyzer.service.analytics.ProductivityInsightsBuilder;
import com.bankanalyzer.service.analytics.RecommendationEngine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests pinning {@link SpendingAnalyticsService} behavior (Phase 0
 * safety net) through its public facade methods — these fixture-based, exact-value
 * assertions must produce byte-identical output before and after the Phase 1.2 split
 * into {@code com.bankanalyzer.service.analytics}. Internals now covered by dedicated
 * tests per new class (e.g. {@code CategorySpendingCalculatorTest}).
 */
public class SpendingAnalyticsServiceTest {

    private static final double DELTA = 0.01;

    private final CategorySpendingCalculator categorySpendingCalculator =
            new CategorySpendingCalculator(new CategoryLabelProvider());

    private final SpendingAnalyticsService service = new SpendingAnalyticsService(
            categorySpendingCalculator,
            new ProductivityInsightsBuilder(
                    categorySpendingCalculator,
                    new BudgetRuleAnalyzer(),
                    new FinancialHealthScorer(),
                    new RecommendationEngine()));

    @Test
    void buildCategorySpendingComputesTotalsAndDateRange() {
        CategorySpendingResponse resp = service.buildCategorySpending(fixture());

        assertEquals(29700.0, resp.getTotalSpend(), DELTA);
        assertEquals("2024-01 to 2024-02", resp.getDateRange());
        assertEquals(2, resp.getTotalMonths());

        assertEquals(15500.0, resp.getFood().getTotalSpend(), DELTA); // FOOD_DINING(6500) + GROCERIES(9000)
        assertEquals(6000.0, resp.getHotelAndMerchant().getTotalSpend(), DELTA);
        assertEquals(0.0, resp.getEntertainment().getTotalSpend(), DELTA);
        assertEquals(1900.0, resp.getTravel().getTotalSpend(), DELTA);

        assertEquals(6, resp.getAllCategories().size());
    }

    /**
     * Two months (Jan/Feb 2024) of transactions across distinct, non-tying category
     * totals (GROCERIES 9000 > FOOD_DINING 6500 > SHOPPING 6000 > UTILITIES 3800 >
     * INVESTMENT 2500 > FUEL 1900) plus salary income, so sort order is deterministic.
     */
    private List<Transaction> fixture() {
        return List.of(
                tx(2024, 1, 1, 0, 100000, Category.SALARY_INCOME),
                tx(2024, 2, 1, 0, 100000, Category.SALARY_INCOME),
                tx(2024, 1, 5, 5000, 0, Category.GROCERIES),
                tx(2024, 2, 5, 4000, 0, Category.GROCERIES),
                tx(2024, 1, 10, 3000, 0, Category.FOOD_DINING),
                tx(2024, 2, 10, 3500, 0, Category.FOOD_DINING),
                tx(2024, 1, 15, 2000, 0, Category.UTILITIES),
                tx(2024, 2, 15, 1800, 0, Category.UTILITIES),
                tx(2024, 1, 20, 1000, 0, Category.FUEL),
                tx(2024, 2, 20, 900, 0, Category.FUEL),
                tx(2024, 1, 25, 6000, 0, Category.SHOPPING),
                tx(2024, 1, 28, 2500, 0, Category.INVESTMENT)
        );
    }

    private Transaction tx(int y, int m, int d, double debit, double credit, Category cat) {
        return Transaction.builder()
                .date(LocalDate.of(y, m, d))
                .description("test")
                .debit(debit).credit(credit).balance(0)
                .category(cat)
                .build();
    }

    @Test
    void allCategoriesAreSortedByTotalSpendDescending() {
        CategorySpendingResponse resp = service.buildCategorySpending(fixture());
        List<CategoryDetail> all = resp.getAllCategories();

        assertEquals("Groceries", all.get(0).getCategoryName());
        assertEquals(9000.0, all.get(0).getTotalSpend(), DELTA);
        assertEquals("Food & Dining", all.get(1).getCategoryName());
        assertEquals(6500.0, all.get(1).getTotalSpend(), DELTA);
        assertEquals("Shopping", all.get(2).getCategoryName());
        assertEquals(6000.0, all.get(2).getTotalSpend(), DELTA);
        assertEquals("Utilities", all.get(3).getCategoryName());
        assertEquals(3800.0, all.get(3).getTotalSpend(), DELTA);
        assertEquals("Investment & SIP", all.get(4).getCategoryName());
        assertEquals(2500.0, all.get(4).getTotalSpend(), DELTA);
        assertEquals("Fuel", all.get(5).getCategoryName());
        assertEquals(1900.0, all.get(5).getTotalSpend(), DELTA);
    }

    @Test
    void buildProductivityInsightsComputesIncomeAndSavings() {
        ProductivityInsightsResponse resp = service.buildProductivityInsights(fixture());

        assertEquals(200000.0, resp.getTotalIncome(), DELTA);
        assertEquals(29700.0, resp.getTotalSpend(), DELTA);
        assertEquals(170300.0, resp.getNetSavings(), DELTA);
        assertEquals(85.15, resp.getSavingsRate(), DELTA);
        assertEquals(14700.0, resp.getEssentialSpend(), DELTA);
        assertEquals(12500.0, resp.getDiscretionarySpend(), DELTA);
        assertEquals(49.49, resp.getEssentialPercent(), DELTA);
        assertEquals(42.09, resp.getDiscretionaryPercent(), DELTA);
    }

    @Test
    void buildProductivityInsightsComputesBudgetRule() {
        BudgetRuleAnalysis rule = service.buildProductivityInsights(fixture()).getBudgetRuleAnalysis();

        assertEquals(50.0, rule.getNeedsTargetPercent(), DELTA);
        assertEquals(30.0, rule.getWantsTargetPercent(), DELTA);
        assertEquals(20.0, rule.getSavingsTargetPercent(), DELTA);

        assertEquals(14700.0, rule.getNeedsAmount(), DELTA);
        assertEquals(12500.0, rule.getWantsAmount(), DELTA);
        assertEquals(2500.0, rule.getSavingsAmount(), DELTA);

        assertEquals(49.49, rule.getNeedsActualPercent(), DELTA);
        assertEquals(42.09, rule.getWantsActualPercent(), DELTA);
        assertEquals(1.25, rule.getSavingsActualPercent(), DELTA);

        assertEquals("ON_TARGET", rule.getNeedsStatus());
        assertEquals("OVER", rule.getWantsStatus());
        assertEquals("NEEDS_ATTENTION", rule.getSavingsStatus());

        assertEquals(18750.0, rule.getSavingsGapMonthly(), DELTA);
    }

    @Test
    void buildProductivityInsightsRanksRecommendationsByCategoryOrder() {
        List<SpendingRecommendation> recs =
                service.buildProductivityInsights(fixture()).getRecommendations();

        // Groceries, Food & Dining, Shopping, Fuel exceed their per-category benchmark;
        // Utilities/Investment have no specific benchmark (default 100%) so are skipped.
        // A trailing "Savings & Investment" recommendation is added since savings % < 20.
        assertEquals(5, recs.size());

        assertEquals("Groceries", recs.get(0).getCategory());
        assertEquals("REDUCE", recs.get(0).getAction());
        assertEquals(1, recs.get(0).getPriority());
        assertEquals(4500.0, recs.get(0).getCurrentMonthlyAmount(), DELTA);
        assertEquals(1485.0, recs.get(0).getTargetMonthlyAmount(), DELTA);
        assertEquals(3015.0, recs.get(0).getPotentialMonthlySavings(), DELTA);

        assertEquals("Food & Dining", recs.get(1).getCategory());
        assertEquals(2, recs.get(1).getPriority());
        assertEquals(3250.0, recs.get(1).getCurrentMonthlyAmount(), DELTA);
        assertEquals(2227.5, recs.get(1).getTargetMonthlyAmount(), DELTA);
        assertEquals(1022.5, recs.get(1).getPotentialMonthlySavings(), DELTA);

        assertEquals("Shopping", recs.get(2).getCategory());
        assertEquals(3, recs.get(2).getPriority());
        assertEquals(6000.0, recs.get(2).getCurrentMonthlyAmount(), DELTA);
        assertEquals(1485.0, recs.get(2).getTargetMonthlyAmount(), DELTA);
        assertEquals(4515.0, recs.get(2).getPotentialMonthlySavings(), DELTA);

        assertEquals("Fuel", recs.get(3).getCategory());
        assertEquals(4, recs.get(3).getPriority());
        assertEquals(950.0, recs.get(3).getCurrentMonthlyAmount(), DELTA);
        assertEquals(742.5, recs.get(3).getTargetMonthlyAmount(), DELTA);
        assertEquals(207.5, recs.get(3).getPotentialMonthlySavings(), DELTA);

        assertEquals("Savings & Investment", recs.get(4).getCategory());
        assertEquals("INCREASE", recs.get(4).getAction());
        assertEquals(5, recs.get(4).getPriority());
        assertEquals(1250.0, recs.get(4).getCurrentMonthlyAmount(), DELTA);
        assertEquals(20000.0, recs.get(4).getTargetMonthlyAmount(), DELTA);
        assertEquals(-18750.0, recs.get(4).getPotentialMonthlySavings(), DELTA);
    }
}

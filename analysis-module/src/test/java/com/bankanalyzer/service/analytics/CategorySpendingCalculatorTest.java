package com.bankanalyzer.service.analytics;

import com.bankanalyzer.api.dto.CategoryDetail;
import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Focused unit tests for {@link CategorySpendingCalculator}, extracted from the
 * former monolithic {@code SpendingAnalyticsServiceTest} as part of the Phase 1.2 split.
 */
public class CategorySpendingCalculatorTest {

    private static final double DELTA = 0.01;

    private final CategorySpendingCalculator calculator =
            new CategorySpendingCalculator(new CategoryLabelProvider());

    @Test
    void linearSlopePinsRegressionFormula() {
        assertEquals(100.0, calculator.linearSlope(List.of(1000.0, 1100.0, 1200.0)), DELTA);
        assertEquals(0.0, calculator.linearSlope(List.of(5.0)), DELTA);
        assertEquals(0.0, calculator.linearSlope(List.of()), DELTA);
    }

    @Test
    void buildGroupAggregatesOnlyMatchingCategories() {
        List<Transaction> debits = List.of(
                tx(2024, 1, 5, 5000, Category.GROCERIES),
                tx(2024, 2, 5, 4000, Category.GROCERIES),
                tx(2024, 1, 10, 3000, Category.FOOD_DINING),
                tx(2024, 2, 10, 3500, Category.FOOD_DINING),
                tx(2024, 1, 20, 1000, Category.FUEL)
        );

        CategoryDetail food = calculator.buildGroup("Food & Groceries",
                List.of("FOOD_DINING", "GROCERIES"),
                Set.of(Category.FOOD_DINING, Category.GROCERIES),
                debits, 16500.0);

        assertEquals(15500.0, food.getTotalSpend(), DELTA); // 9000 groceries + 6500 food
    }

    private Transaction tx(int y, int m, int d, double debit, Category cat) {
        return Transaction.builder()
                .date(LocalDate.of(y, m, d))
                .description("test")
                .debit(debit).credit(0).balance(0)
                .category(cat)
                .build();
    }
}

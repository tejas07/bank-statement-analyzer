package com.bankanalyzer.service.analytics;

import com.bankanalyzer.model.Category;

import java.util.List;
import java.util.Set;

/**
 * Static category-group configuration shared by the analytics classes —
 * which {@link Category} values roll up into each focus group and 50/30/20 bucket.
 */
public final class CategoryGroupDefinitions {

    public static final Set<Category> FOOD_CATS =
            Set.of(Category.FOOD_DINING, Category.GROCERIES);

    public static final Set<Category> HOTEL_MERCHANT_CATS =
            Set.of(Category.SHOPPING);

    public static final Set<Category> ENTERTAINMENT_CATS =
            Set.of(Category.ENTERTAINMENT);

    public static final Set<Category> TRAVEL_CATS =
            Set.of(Category.TRAVEL, Category.FUEL);

    // 50/30/20 rule buckets
    public static final Set<Category> NEEDS_CATS =
            Set.of(Category.UTILITIES, Category.EMI_LOAN, Category.GROCERIES,
                    Category.HEALTH, Category.FUEL);

    public static final Set<Category> WANTS_CATS =
            Set.of(Category.FOOD_DINING, Category.ENTERTAINMENT, Category.SHOPPING,
                    Category.TRAVEL, Category.EDUCATION);

    public static final Set<Category> SAVINGS_CATS =
            Set.of(Category.INVESTMENT);

    public static final List<String> NEEDS_LABELS =
            List.of("Utilities", "EMI/Loans", "Groceries", "Health", "Fuel");

    public static final List<String> WANTS_LABELS =
            List.of("Food/Dining", "Entertainment", "Shopping", "Travel", "Education");

    public static final List<String> SAVINGS_LABELS =
            List.of("Investment", "SIP");

    private CategoryGroupDefinitions() {
    }
}

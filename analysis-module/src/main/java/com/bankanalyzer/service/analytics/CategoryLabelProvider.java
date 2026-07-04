package com.bankanalyzer.service.analytics;

import com.bankanalyzer.model.Category;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Table-driven display labels for {@link Category} values.
 */
@Component
public class CategoryLabelProvider {

    private static final Map<Category, String> LABELS = new EnumMap<>(Category.class);

    static {
        LABELS.put(Category.FOOD_DINING, "Food & Dining");
        LABELS.put(Category.GROCERIES, "Groceries");
        LABELS.put(Category.SHOPPING, "Shopping");
        LABELS.put(Category.FUEL, "Fuel");
        LABELS.put(Category.UTILITIES, "Utilities");
        LABELS.put(Category.HEALTH, "Health & Medical");
        LABELS.put(Category.ENTERTAINMENT, "Entertainment");
        LABELS.put(Category.TRAVEL, "Travel");
        LABELS.put(Category.EDUCATION, "Education");
        LABELS.put(Category.EMI_LOAN, "EMI & Loans");
        LABELS.put(Category.INVESTMENT, "Investment & SIP");
        LABELS.put(Category.SALARY_INCOME, "Salary / Income");
        LABELS.put(Category.TRANSFER, "Transfers");
    }

    public String labelFor(Category category) {
        return LABELS.getOrDefault(category, "Other");
    }
}

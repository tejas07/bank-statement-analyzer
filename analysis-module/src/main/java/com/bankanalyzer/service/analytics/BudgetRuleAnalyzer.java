package com.bankanalyzer.service.analytics;

import com.bankanalyzer.api.dto.BudgetRuleAnalysis;
import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.Transaction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.bankanalyzer.service.analytics.TransactionMath.round;

/**
 * 50/30/20 budget rule computation: needs/wants/savings amounts, percentages, and status.
 */
@Component
public class BudgetRuleAnalyzer {

    public BudgetRuleAnalysis analyze(List<Transaction> debits, double totalSpend,
                                      double totalIncome, int months) {

        double needsAmount = round(categorySum(debits, CategoryGroupDefinitions.NEEDS_CATS));
        double wantsAmount = round(categorySum(debits, CategoryGroupDefinitions.WANTS_CATS));
        double savingsAmount = round(categorySum(debits, CategoryGroupDefinitions.SAVINGS_CATS));

        double needsPct = totalSpend > 0 ? round(needsAmount / totalSpend * 100) : 0;
        double wantsPct = totalSpend > 0 ? round(wantsAmount / totalSpend * 100) : 0;
        double savingsPct = totalIncome > 0 ? round(savingsAmount / totalIncome * 100) : 0;

        double savingsGapMonthly = savingsPct < 20 && totalIncome > 0
                ? round(totalIncome * 0.20 / months - savingsAmount / months)
                : 0;

        return BudgetRuleAnalysis.builder()
                .needsTargetPercent(50.0).wantsTargetPercent(30.0).savingsTargetPercent(20.0)
                .needsActualPercent(needsPct).wantsActualPercent(wantsPct)
                .savingsActualPercent(savingsPct)
                .needsAmount(needsAmount).wantsAmount(wantsAmount).savingsAmount(savingsAmount)
                .needsStatus(status(needsPct, 50)).wantsStatus(status(wantsPct, 30))
                .savingsStatus(savingsStatus(savingsPct))
                .needsCategories(CategoryGroupDefinitions.NEEDS_LABELS)
                .wantsCategories(CategoryGroupDefinitions.WANTS_LABELS)
                .savingsCategories(CategoryGroupDefinitions.SAVINGS_LABELS)
                .savingsGapMonthly(savingsGapMonthly)
                .build();
    }

    private double categorySum(List<Transaction> debits, Set<Category> cats) {
        return debits.stream()
                .filter(t -> cats.contains(t.getCategory()))
                .mapToDouble(Transaction::getDebit)
                .sum();
    }

    private String status(double actual, double target) {
        double delta = actual - target;
        if (Math.abs(delta) <= 5) return "ON_TARGET";
        return delta > 0 ? "OVER" : "UNDER";
    }

    private String savingsStatus(double actual) {
        if (actual >= 20) return "ON_TARGET";
        if (actual >= 10) return "UNDER";
        return "NEEDS_ATTENTION";
    }
}

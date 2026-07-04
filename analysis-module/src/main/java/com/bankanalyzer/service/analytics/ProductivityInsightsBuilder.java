package com.bankanalyzer.service.analytics;

import com.bankanalyzer.api.dto.BudgetRuleAnalysis;
import com.bankanalyzer.api.dto.CategoryDetail;
import com.bankanalyzer.api.dto.ProductivityInsightsResponse;
import com.bankanalyzer.api.dto.SpendingRecommendation;
import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.bankanalyzer.service.analytics.TransactionMath.debits;
import static com.bankanalyzer.service.analytics.TransactionMath.distinctMonths;
import static com.bankanalyzer.service.analytics.TransactionMath.round;

/**
 * Orchestrates {@link CategorySpendingCalculator}, {@link BudgetRuleAnalyzer},
 * {@link FinancialHealthScorer}, and {@link RecommendationEngine} into the full
 * {@link ProductivityInsightsResponse}.
 */
@Component
@RequiredArgsConstructor
public class ProductivityInsightsBuilder {

    private final CategorySpendingCalculator categorySpendingCalculator;
    private final BudgetRuleAnalyzer budgetRuleAnalyzer;
    private final FinancialHealthScorer financialHealthScorer;
    private final RecommendationEngine recommendationEngine;

    public ProductivityInsightsResponse build(List<Transaction> transactions) {
        List<Transaction> debits = debits(transactions);
        List<Transaction> credits = transactions.stream()
                .filter(Transaction::isCredit).collect(Collectors.toList());

        double totalSpend = round(debits.stream().mapToDouble(Transaction::getDebit).sum());
        double totalIncome = round(
                transactions.stream()
                        .filter(t -> t.getCategory() == Category.SALARY_INCOME)
                        .mapToDouble(t -> t.isCredit() ? t.getCredit() : t.getDebit())
                        .sum()
        );
        // If salary is not detected use total credits as a proxy
        if (totalIncome == 0) {
            totalIncome = round(credits.stream().mapToDouble(Transaction::getCredit).sum());
        }

        double netSavings = round(totalIncome - totalSpend);
        double savingsRate = totalIncome > 0 ? round((netSavings / totalIncome) * 100) : 0;

        int months = Math.max(1, distinctMonths(debits));

        BudgetRuleAnalysis budgetRule = budgetRuleAnalyzer.analyze(debits, totalSpend, totalIncome, months);

        // Essential vs discretionary
        double essentialSpend = round(budgetRule.getNeedsAmount());
        double discretionarySpend = round(budgetRule.getWantsAmount());
        double essentialPct = totalSpend > 0 ? round(essentialSpend / totalSpend * 100) : 0;
        double discretionaryPct = totalSpend > 0 ? round(discretionarySpend / totalSpend * 100) : 0;

        List<CategoryDetail> topCategories = categorySpendingCalculator.buildAllCategories(debits, totalSpend);

        List<SpendingRecommendation> recommendations = recommendationEngine.buildRecommendations(
                topCategories, totalIncome, totalSpend, months, budgetRule.getSavingsActualPercent());

        int healthScore = financialHealthScorer.computeHealthScore(
                savingsRate, budgetRule.getWantsActualPercent(), recommendations.size());

        // Date range for daily average
        long days = daySpan(debits);
        double avgDaily = days > 0 ? round(totalSpend / days) : 0;
        double projectedAnnual = round(avgDaily * 365);
        double emergencyMonths = totalSpend > 0 ? round(netSavings / (totalSpend / months)) : 0;

        return ProductivityInsightsResponse.builder()
                .financialHealthScore(healthScore)
                .healthRating(financialHealthScorer.healthRating(healthScore))
                .totalIncome(totalIncome)
                .totalSpend(totalSpend)
                .netSavings(netSavings)
                .savingsRate(savingsRate)
                .budgetRuleAnalysis(budgetRule)
                .essentialSpend(essentialSpend)
                .discretionarySpend(discretionarySpend)
                .essentialPercent(essentialPct)
                .discretionaryPercent(discretionaryPct)
                .topSpendingCategories(topCategories)
                .recommendations(recommendations)
                .averageDailySpend(avgDaily)
                .projectedAnnualSpend(projectedAnnual)
                .emergencyFundMonths(emergencyMonths)
                .build();
    }

    private long daySpan(List<Transaction> debits) {
        Optional<LocalDate> min = debits.stream()
                .filter(t -> t.getDate() != null).map(Transaction::getDate).min(Comparator.naturalOrder());
        Optional<LocalDate> max = debits.stream()
                .filter(t -> t.getDate() != null).map(Transaction::getDate).max(Comparator.naturalOrder());
        if (min.isEmpty() || max.isEmpty()) return 30;
        long days = ChronoUnit.DAYS.between(min.get(), max.get());
        return days == 0 ? 1 : days;
    }
}

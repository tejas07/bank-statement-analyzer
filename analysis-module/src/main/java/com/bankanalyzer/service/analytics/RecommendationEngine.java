package com.bankanalyzer.service.analytics;

import com.bankanalyzer.api.dto.CategoryDetail;
import com.bankanalyzer.api.dto.SpendingRecommendation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bankanalyzer.service.analytics.TransactionMath.round;

/**
 * Ranked, actionable spending recommendations, driven by a table of per-category benchmarks.
 */
@Component
public class RecommendationEngine {

    private static final Map<String, CategoryBenchmark> BENCHMARKS = Map.of(
            "Food & Dining", new CategoryBenchmark(15.0,
                    "Batch-cook meals at home 3–4 days a week to cut restaurant spend."),
            "Groceries", new CategoryBenchmark(10.0,
                    "Plan a weekly shopping list and use grocery apps for best prices."),
            "Entertainment", new CategoryBenchmark(8.0,
                    "Audit streaming subscriptions — cancel unused ones and share family plans."),
            "Shopping", new CategoryBenchmark(10.0,
                    "Wait 48 hours before non-essential purchases to reduce impulse buying."),
            "Travel", new CategoryBenchmark(10.0,
                    "Book tickets 3–4 weeks in advance and use fare-alert apps."),
            "Fuel", new CategoryBenchmark(5.0,
                    "Consider carpooling or using public transport for regular commutes.")
    );
    private static final CategoryBenchmark DEFAULT_BENCHMARK = new CategoryBenchmark(100.0,
            "Review this category monthly to track whether spending is justified.");

    /**
     * Thresholds derived from the 50/30/20 rule and common personal-finance benchmarks.
     */
    private record CategoryBenchmark(double targetPct, String tip) {
    }

    public List<SpendingRecommendation> buildRecommendations(
            List<CategoryDetail> topCats, double totalIncome,
            double totalSpend, int months, double savingsPct) {

        List<SpendingRecommendation> recs = new ArrayList<>();
        int priority = 1;

        for (CategoryDetail cat : topCats) {
            double pct = cat.getPercentageOfTotal();
            double monthly = cat.getAverageMonthlySpend();
            String name = cat.getCategoryName();

            CategoryBenchmark benchmark = BENCHMARKS.getOrDefault(name, DEFAULT_BENCHMARK);
            double targetPct = benchmark.targetPct();

            if (pct > targetPct && monthly > 500) {
                double targetMonthly = round(totalSpend / months * targetPct / 100);
                double saving = round(Math.max(0, monthly - targetMonthly));
                if (saving > 0) {
                    recs.add(SpendingRecommendation.builder()
                            .priority(priority++)
                            .category(name)
                            .action("REDUCE")
                            .message(buildMessage(name, pct, targetPct, saving, benchmark.tip()))
                            .currentMonthlyAmount(monthly)
                            .targetMonthlyAmount(targetMonthly)
                            .potentialMonthlySavings(saving)
                            .annualSavingsPotential(round(saving * 12))
                            .build());
                }
            }
        }

        // Savings gap recommendation
        if (savingsPct < 20 && totalIncome > 0) {
            double monthlyIncome = totalIncome / months;
            double currentSaving = monthlyIncome * savingsPct / 100;
            double targetSaving = monthlyIncome * 0.20;
            double gap = round(targetSaving - currentSaving);
            recs.add(SpendingRecommendation.builder()
                    .priority(priority)
                    .category("Savings & Investment")
                    .action("INCREASE")
                    .message(String.format(
                            "Your savings rate is %.1f%% (target: 20%%). Increase monthly SIP/RD by ₹%.0f " +
                                    "to reach the 20%% benchmark and build long-term wealth.", savingsPct, gap))
                    .currentMonthlyAmount(round(currentSaving))
                    .targetMonthlyAmount(round(targetSaving))
                    .potentialMonthlySavings(-gap)
                    .annualSavingsPotential(round(gap * 12))
                    .build());
        }

        return recs;
    }

    private String buildMessage(String category, double actual, double target, double saving, String tip) {
        return String.format(
                "%s accounts for %.1f%% of your spend (benchmark: %.0f%%). " +
                        "Reducing by ₹%.0f/month could save ₹%.0f/year. " +
                        "Tip: %s",
                category, actual, target, saving, saving * 12, tip);
    }
}

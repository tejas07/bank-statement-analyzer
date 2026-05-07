package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.*;
import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyses parsed transactions and produces three response objects:
 * <ul>
 *   <li>{@link CategorySpendingResponse}   — historical spend by category group</li>
 *   <li>{@link ProductivityInsightsResponse} — financial health + 50/30/20 rule</li>
 * </ul>
 * The {@link SpendingForecastResponse} is built by {@link ForecastService} using
 * the {@link CategoryDetail} objects returned here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpendingAnalyticsService {

    // ── Category groups ──────────────────────────────────────────────────────

    private static final Set<Category> FOOD_CATS =
        Set.of(Category.FOOD_DINING, Category.GROCERIES);

    private static final Set<Category> HOTEL_MERCHANT_CATS =
        Set.of(Category.SHOPPING);

    private static final Set<Category> ENTERTAINMENT_CATS =
        Set.of(Category.ENTERTAINMENT);

    private static final Set<Category> TRAVEL_CATS =
        Set.of(Category.TRAVEL, Category.FUEL);

    // 50/30/20 rule buckets
    private static final Set<Category> NEEDS_CATS =
        Set.of(Category.UTILITIES, Category.EMI_LOAN, Category.GROCERIES,
               Category.HEALTH, Category.FUEL);

    private static final Set<Category> WANTS_CATS =
        Set.of(Category.FOOD_DINING, Category.ENTERTAINMENT, Category.SHOPPING,
               Category.TRAVEL, Category.EDUCATION);

    private static final Set<Category> SAVINGS_CATS =
        Set.of(Category.INVESTMENT);

    // Trend slope thresholds (₹ per month)
    private static final double TREND_THRESHOLD = 50.0;
    private static final int    TOP_MERCHANTS   = 5;

    // ── Public API ───────────────────────────────────────────────────────────

    public CategorySpendingResponse buildCategorySpending(List<Transaction> transactions) {
        List<Transaction> debits = debits(transactions);
        double totalSpend = round(debits.stream().mapToDouble(Transaction::getDebit).sum());

        CategoryDetail food            = buildGroup("Food & Groceries",
            List.of("FOOD_DINING", "GROCERIES"), FOOD_CATS, debits, totalSpend);
        CategoryDetail hotelMerchant   = buildGroup("Hotel & Merchant",
            List.of("SHOPPING"), HOTEL_MERCHANT_CATS, debits, totalSpend);
        CategoryDetail entertainment   = buildGroup("Entertainment",
            List.of("ENTERTAINMENT"), ENTERTAINMENT_CATS, debits, totalSpend);
        CategoryDetail travel          = buildGroup("Travel & Fuel",
            List.of("TRAVEL", "FUEL"), TRAVEL_CATS, debits, totalSpend);

        List<CategoryDetail> allCategories = buildAllCategories(debits, totalSpend);

        String dateRange   = dateRange(debits);
        int totalMonths    = distinctMonths(debits);

        return CategorySpendingResponse.builder()
            .food(food)
            .hotelAndMerchant(hotelMerchant)
            .entertainment(entertainment)
            .travel(travel)
            .allCategories(allCategories)
            .totalSpend(totalSpend)
            .dateRange(dateRange)
            .totalMonths(totalMonths)
            .build();
    }

    public ProductivityInsightsResponse buildProductivityInsights(List<Transaction> transactions) {
        List<Transaction> debits  = debits(transactions);
        List<Transaction> credits = transactions.stream()
            .filter(Transaction::isCredit).collect(Collectors.toList());

        double totalSpend  = round(debits.stream().mapToDouble(Transaction::getDebit).sum());
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

        double netSavings   = round(totalIncome - totalSpend);
        double savingsRate  = totalIncome > 0 ? round((netSavings / totalIncome) * 100) : 0;

        int months = Math.max(1, distinctMonths(debits));

        // 50/30/20 amounts
        double needsAmount   = round(categorySum(debits, NEEDS_CATS));
        double wantsAmount   = round(categorySum(debits, WANTS_CATS));
        double savingsAmount = round(categorySum(
            transactions.stream().filter(Transaction::isDebit).collect(Collectors.toList()),
            SAVINGS_CATS));

        double needsPct   = totalSpend > 0 ? round(needsAmount   / totalSpend * 100) : 0;
        double wantsPct   = totalSpend > 0 ? round(wantsAmount   / totalSpend * 100) : 0;
        double savingsPct = totalIncome > 0 ? round(savingsAmount / totalIncome * 100) : 0;

        double savingsGapMonthly = savingsPct < 20 && totalIncome > 0
            ? round(totalIncome * 0.20 / months - savingsAmount / months)
            : 0;

        BudgetRuleAnalysis budgetRule = BudgetRuleAnalysis.builder()
            .needsTargetPercent(50.0).wantsTargetPercent(30.0).savingsTargetPercent(20.0)
            .needsActualPercent(needsPct).wantsActualPercent(wantsPct)
            .savingsActualPercent(savingsPct)
            .needsAmount(needsAmount).wantsAmount(wantsAmount).savingsAmount(savingsAmount)
            .needsStatus(status(needsPct, 50)).wantsStatus(status(wantsPct, 30))
            .savingsStatus(savingsStatus(savingsPct))
            .needsCategories(List.of("Utilities", "EMI/Loans", "Groceries", "Health", "Fuel"))
            .wantsCategories(List.of("Food/Dining", "Entertainment", "Shopping", "Travel", "Education"))
            .savingsCategories(List.of("Investment", "SIP"))
            .savingsGapMonthly(savingsGapMonthly)
            .build();

        // Essential vs discretionary
        double essentialSpend      = round(needsAmount);
        double discretionarySpend  = round(wantsAmount);
        double essentialPct        = totalSpend > 0 ? round(essentialSpend   / totalSpend * 100) : 0;
        double discretionaryPct    = totalSpend > 0 ? round(discretionarySpend / totalSpend * 100) : 0;

        List<CategoryDetail> topCategories = buildAllCategories(debits, totalSpend);

        List<SpendingRecommendation> recommendations =
            buildRecommendations(topCategories, totalIncome, totalSpend, months, savingsPct);

        int healthScore = computeHealthScore(savingsRate, wantsPct, recommendations.size());

        // Date range for daily average
        long days = daySpan(debits);
        double avgDaily         = days > 0 ? round(totalSpend / days) : 0;
        double projectedAnnual  = round(avgDaily * 365);
        double emergencyMonths  = totalSpend > 0 ? round(netSavings / (totalSpend / months)) : 0;

        return ProductivityInsightsResponse.builder()
            .financialHealthScore(healthScore)
            .healthRating(healthRating(healthScore))
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

    // ── Group builder ────────────────────────────────────────────────────────

    CategoryDetail buildGroup(String name, List<String> subCats,
                              Set<Category> categories,
                              List<Transaction> debits, double totalSpend) {

        List<Transaction> grouped = debits.stream()
            .filter(t -> categories.contains(t.getCategory()))
            .collect(Collectors.toList());

        return buildDetail(name, subCats, grouped, totalSpend);
    }

    private List<CategoryDetail> buildAllCategories(List<Transaction> debits, double totalSpend) {
        Map<Category, List<Transaction>> byCat = debits.stream()
            .collect(Collectors.groupingBy(Transaction::getCategory));

        return byCat.entrySet().stream()
            .map(e -> buildDetail(
                humanReadable(e.getKey()),
                List.of(e.getKey().name()),
                e.getValue(),
                totalSpend))
            .sorted(Comparator.comparingDouble(CategoryDetail::getTotalSpend).reversed())
            .collect(Collectors.toList());
    }

    private CategoryDetail buildDetail(String name, List<String> subCats,
                                       List<Transaction> txns, double totalSpend) {
        if (txns.isEmpty()) {
            return CategoryDetail.builder()
                .categoryName(name).subCategories(subCats)
                .totalSpend(0).percentageOfTotal(0).averageMonthlySpend(0)
                .momChangePercent(0).trendDirection("STABLE")
                .monthlyBreakdown(List.of()).topMerchants(List.of())
                .highestSpend(0).highestSpendMonth(null)
                .build();
        }

        double total = round(txns.stream().mapToDouble(Transaction::getDebit).sum());
        double pct   = totalSpend > 0 ? round(total / totalSpend * 100) : 0;

        // Monthly breakdown (chronological)
        TreeMap<String, Double> byMonth = new TreeMap<>();
        txns.forEach(t -> {
            if (t.getDate() != null)
                byMonth.merge(t.getMonthKey(), t.getDebit(), Double::sum);
        });

        List<MonthlySpend> monthly = new ArrayList<>();
        String prevMonth = null;
        double prevAmt   = 0;
        for (Map.Entry<String, Double> e : byMonth.entrySet()) {
            double amt    = round(e.getValue());
            double change = prevMonth != null && prevAmt > 0
                ? round((amt - prevAmt) / prevAmt * 100) : 0;
            monthly.add(MonthlySpend.builder()
                .month(e.getKey()).amount(amt).changeFromPrevious(change).build());
            prevMonth = e.getKey();
            prevAmt   = amt;
        }

        int numMonths = Math.max(1, byMonth.size());
        double avgMonthly = round(total / numMonths);

        // Month-over-month change (last vs second-to-last)
        double momChange = 0;
        if (monthly.size() >= 2) {
            momChange = monthly.get(monthly.size() - 1).getChangeFromPrevious();
        }

        // Trend via linear regression
        double slope = linearSlope(new ArrayList<>(byMonth.values()));
        String trend = slope > TREND_THRESHOLD ? "INCREASING"
            : slope < -TREND_THRESHOLD ? "DECREASING" : "STABLE";

        // Highest spend month
        Map.Entry<String, Double> maxEntry = byMonth.entrySet().stream()
            .max(Map.Entry.comparingByValue()).orElse(null);
        double highestSpend = maxEntry != null ? round(maxEntry.getValue()) : 0;
        String highestMonth = maxEntry != null ? maxEntry.getKey() : null;

        // Top merchants
        List<MerchantSummary> topMerchants = txns.stream()
            .collect(Collectors.groupingBy(Transaction::getMerchantName,
                Collectors.summingDouble(Transaction::getDebit)))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(TOP_MERCHANTS)
            .map(e -> MerchantSummary.builder()
                .merchant(e.getKey())
                .count((int) txns.stream()
                    .filter(t -> t.getMerchantName().equals(e.getKey())).count())
                .totalDebit(round(e.getValue()))
                .build())
            .collect(Collectors.toList());

        return CategoryDetail.builder()
            .categoryName(name).subCategories(subCats)
            .totalSpend(total).percentageOfTotal(pct)
            .averageMonthlySpend(avgMonthly).momChangePercent(momChange)
            .trendDirection(trend).monthlyBreakdown(monthly).topMerchants(topMerchants)
            .highestSpend(highestSpend).highestSpendMonth(highestMonth)
            .build();
    }

    // ── Recommendations ──────────────────────────────────────────────────────

    private List<SpendingRecommendation> buildRecommendations(
            List<CategoryDetail> topCats, double totalIncome,
            double totalSpend, int months, double savingsPct) {

        List<SpendingRecommendation> recs = new ArrayList<>();
        int priority = 1;

        for (CategoryDetail cat : topCats) {
            double pct = cat.getPercentageOfTotal();
            double monthly = cat.getAverageMonthlySpend();
            String name = cat.getCategoryName();

            // Thresholds derived from the 50/30/20 rule and common personal-finance benchmarks
            double targetPct = switch (name) {
                case "Food & Dining"   -> 15.0;
                case "Groceries"       -> 10.0;
                case "Entertainment"   ->  8.0;
                case "Shopping"        -> 10.0;
                case "Travel"          -> 10.0;
                case "Fuel"            ->  5.0;
                default                -> 100.0; // no specific threshold
            };

            if (pct > targetPct && monthly > 500) {
                double targetMonthly = round(totalSpend / months * targetPct / 100);
                double saving = round(Math.max(0, monthly - targetMonthly));
                if (saving > 0) {
                    recs.add(SpendingRecommendation.builder()
                        .priority(priority++)
                        .category(name)
                        .action("REDUCE")
                        .message(buildMessage(name, pct, targetPct, saving))
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
            double targetSaving  = monthlyIncome * 0.20;
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

    private String buildMessage(String category, double actual, double target, double saving) {
        return String.format(
            "%s accounts for %.1f%% of your spend (benchmark: %.0f%%). " +
            "Reducing by ₹%.0f/month could save ₹%.0f/year. " +
            "Tip: %s",
            category, actual, target, saving, saving * 12,
            tipFor(category));
    }

    private String tipFor(String category) {
        return switch (category) {
            case "Food & Dining"  ->
                "Batch-cook meals at home 3–4 days a week to cut restaurant spend.";
            case "Groceries"      ->
                "Plan a weekly shopping list and use grocery apps for best prices.";
            case "Entertainment"  ->
                "Audit streaming subscriptions — cancel unused ones and share family plans.";
            case "Shopping"       ->
                "Wait 48 hours before non-essential purchases to reduce impulse buying.";
            case "Travel"         ->
                "Book tickets 3–4 weeks in advance and use fare-alert apps.";
            case "Fuel"           ->
                "Consider carpooling or using public transport for regular commutes.";
            default               ->
                "Review this category monthly to track whether spending is justified.";
        };
    }

    // ── Health score ─────────────────────────────────────────────────────────

    private int computeHealthScore(double savingsRate, double wantsPct, int recsCount) {
        int score = 0;

        // Savings rate (40 pts)
        if (savingsRate >= 30)       score += 40;
        else if (savingsRate >= 20)  score += 30;
        else if (savingsRate >= 10)  score += 20;
        else if (savingsRate >= 0)   score += 10;

        // Discretionary control (35 pts)
        if (wantsPct <= 25)          score += 35;
        else if (wantsPct <= 30)     score += 25;
        else if (wantsPct <= 40)     score += 15;
        else if (wantsPct <= 50)     score += 5;

        // Recommendation count — fewer = better (25 pts)
        if (recsCount == 0)          score += 25;
        else if (recsCount <= 2)     score += 15;
        else if (recsCount <= 4)     score += 8;

        return Math.min(100, score);
    }

    private String healthRating(int score) {
        if (score >= 80) return "EXCELLENT";
        if (score >= 60) return "GOOD";
        if (score >= 40) return "FAIR";
        return "NEEDS_ATTENTION";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<Transaction> debits(List<Transaction> txns) {
        return txns.stream().filter(Transaction::isDebit).collect(Collectors.toList());
    }

    private double categorySum(List<Transaction> debits, Set<Category> cats) {
        return debits.stream()
            .filter(t -> cats.contains(t.getCategory()))
            .mapToDouble(Transaction::getDebit)
            .sum();
    }

    /** Least-squares slope over an ordered value list. Returns 0 for < 2 data points. */
    double linearSlope(List<Double> values) {
        int n = values.size();
        if (n < 2) return 0;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX  += i;
            sumY  += values.get(i);
            sumXY += (double) i * values.get(i);
            sumX2 += (double) i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        return denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
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

    private String dateRange(List<Transaction> debits) {
        if (debits.isEmpty()) return "N/A";
        Optional<LocalDate> min = debits.stream()
            .filter(t -> t.getDate() != null).map(Transaction::getDate).min(Comparator.naturalOrder());
        Optional<LocalDate> max = debits.stream()
            .filter(t -> t.getDate() != null).map(Transaction::getDate).max(Comparator.naturalOrder());
        if (min.isEmpty() || max.isEmpty()) return "N/A";
        String from = min.get().getYear() + "-" + String.format("%02d", min.get().getMonthValue());
        String to   = max.get().getYear() + "-" + String.format("%02d", max.get().getMonthValue());
        return from.equals(to) ? from : from + " to " + to;
    }

    private int distinctMonths(List<Transaction> debits) {
        return (int) debits.stream()
            .filter(t -> t.getDate() != null)
            .map(Transaction::getMonthKey)
            .distinct().count();
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

    private String humanReadable(Category c) {
        return switch (c) {
            case FOOD_DINING    -> "Food & Dining";
            case GROCERIES      -> "Groceries";
            case SHOPPING       -> "Shopping";
            case FUEL           -> "Fuel";
            case UTILITIES      -> "Utilities";
            case HEALTH         -> "Health & Medical";
            case ENTERTAINMENT  -> "Entertainment";
            case TRAVEL         -> "Travel";
            case EDUCATION      -> "Education";
            case EMI_LOAN       -> "EMI & Loans";
            case INVESTMENT     -> "Investment & SIP";
            case SALARY_INCOME  -> "Salary / Income";
            case TRANSFER       -> "Transfers";
            default             -> "Other";
        };
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
}

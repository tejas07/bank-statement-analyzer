package com.bankanalyzer.service.analytics;

import com.bankanalyzer.api.dto.CategoryDetail;
import com.bankanalyzer.api.dto.CategorySpendingResponse;
import com.bankanalyzer.api.dto.MerchantSummary;
import com.bankanalyzer.api.dto.MonthlySpend;
import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.bankanalyzer.service.analytics.TransactionMath.debits;
import static com.bankanalyzer.service.analytics.TransactionMath.round;

/**
 * Historical spend by category (or category group), including monthly breakdown,
 * trend direction (via {@link #linearSlope}), and top merchants.
 */
@Component
@RequiredArgsConstructor
public class CategorySpendingCalculator {

    // Trend slope threshold (₹ per month)
    private static final double TREND_THRESHOLD = 50.0;
    private static final int TOP_MERCHANTS = 5;

    private final CategoryLabelProvider categoryLabelProvider;

    public CategorySpendingResponse buildCategorySpending(List<Transaction> transactions) {
        List<Transaction> debits = debits(transactions);
        double totalSpend = round(debits.stream().mapToDouble(Transaction::getDebit).sum());

        CategoryDetail food = buildGroup("Food & Groceries",
                List.of("FOOD_DINING", "GROCERIES"), CategoryGroupDefinitions.FOOD_CATS, debits, totalSpend);
        CategoryDetail hotelMerchant = buildGroup("Hotel & Merchant",
                List.of("SHOPPING"), CategoryGroupDefinitions.HOTEL_MERCHANT_CATS, debits, totalSpend);
        CategoryDetail entertainment = buildGroup("Entertainment",
                List.of("ENTERTAINMENT"), CategoryGroupDefinitions.ENTERTAINMENT_CATS, debits, totalSpend);
        CategoryDetail travel = buildGroup("Travel & Fuel",
                List.of("TRAVEL", "FUEL"), CategoryGroupDefinitions.TRAVEL_CATS, debits, totalSpend);

        List<CategoryDetail> allCategories = buildAllCategories(debits, totalSpend);

        String dateRange = dateRange(debits);
        int totalMonths = TransactionMath.distinctMonths(debits);

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

    public CategoryDetail buildGroup(String name, List<String> subCats,
                                     Set<Category> categories,
                                     List<Transaction> debits, double totalSpend) {

        List<Transaction> grouped = debits.stream()
                .filter(t -> categories.contains(t.getCategory()))
                .collect(Collectors.toList());

        return buildDetail(name, subCats, grouped, totalSpend);
    }

    public List<CategoryDetail> buildAllCategories(List<Transaction> debits, double totalSpend) {
        Map<Category, List<Transaction>> byCat = debits.stream()
                .collect(Collectors.groupingBy(Transaction::getCategory));

        return byCat.entrySet().stream()
                .map(e -> buildDetail(
                        categoryLabelProvider.labelFor(e.getKey()),
                        List.of(e.getKey().name()),
                        e.getValue(),
                        totalSpend))
                .sorted(Comparator.comparingDouble(CategoryDetail::getTotalSpend).reversed())
                .collect(Collectors.toList());
    }

    public CategoryDetail buildDetail(String name, List<String> subCats,
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
        double pct = totalSpend > 0 ? round(total / totalSpend * 100) : 0;

        // Monthly breakdown (chronological)
        TreeMap<String, Double> byMonth = new TreeMap<>();
        txns.forEach(t -> {
            if (t.getDate() != null)
                byMonth.merge(t.getMonthKey(), t.getDebit(), Double::sum);
        });

        List<MonthlySpend> monthly = new ArrayList<>();
        String prevMonth = null;
        double prevAmt = 0;
        for (Map.Entry<String, Double> e : byMonth.entrySet()) {
            double amt = round(e.getValue());
            double change = prevMonth != null && prevAmt > 0
                    ? round((amt - prevAmt) / prevAmt * 100) : 0;
            monthly.add(MonthlySpend.builder()
                    .month(e.getKey()).amount(amt).changeFromPrevious(change).build());
            prevMonth = e.getKey();
            prevAmt = amt;
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

    /**
     * Least-squares slope over an ordered value list. Returns 0 for < 2 data points.
     */
    public double linearSlope(List<Double> values) {
        int n = values.size();
        if (n < 2) return 0;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += (double) i * values.get(i);
            sumX2 += (double) i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        return denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
    }

    private String dateRange(List<Transaction> debits) {
        if (debits.isEmpty()) return "N/A";
        Optional<LocalDate> min = debits.stream()
                .filter(t -> t.getDate() != null).map(Transaction::getDate).min(Comparator.naturalOrder());
        Optional<LocalDate> max = debits.stream()
                .filter(t -> t.getDate() != null).map(Transaction::getDate).max(Comparator.naturalOrder());
        if (min.isEmpty() || max.isEmpty()) return "N/A";
        String from = min.get().getYear() + "-" + String.format("%02d", min.get().getMonthValue());
        String to = max.get().getYear() + "-" + String.format("%02d", max.get().getMonthValue());
        return from.equals(to) ? from : from + " to " + to;
    }
}

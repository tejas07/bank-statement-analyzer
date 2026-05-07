package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.*;
import com.bankanalyzer.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Produces a {@link SpendingForecastResponse} by combining historical category data
 * from {@link SpendingAnalyticsService} with an inflation-adjusted, multi-scenario
 * statistical projection.
 *
 * <h3>Methodology</h3>
 * <ol>
 *   <li><b>Linear regression</b> — least-squares slope over the monthly historical series.</li>
 *   <li><b>Inflation compounding</b> — monthly rate = (1 + annualRate)^(1/12) − 1.</li>
 *   <li>Three scenarios per month <em>k</em> (1-indexed):
 *     <ul>
 *       <li>Conservative:  avg × 0.90 × (1 + monthlyRate)^k</li>
 *       <li>Baseline:      avg        × (1 + monthlyRate)^k</li>
 *       <li>Pessimistic:   (intercept + slope × (histLen + k − 1)) × (1 + monthlyRate)^k</li>
 *     </ul>
 *   </li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final SpendingAnalyticsService analyticsService;

    private static final double DEFAULT_INFLATION_RATE = 6.0;
    private static final int    DEFAULT_FORECAST_MONTHS = 6;

    // ── Public API ───────────────────────────────────────────────────────────

    public SpendingForecastResponse forecast(List<Transaction> transactions,
                                             int forecastMonths,
                                             double annualInflationRate) {

        CategorySpendingResponse spending = analyticsService.buildCategorySpending(transactions);

        CategoryForecast food        = project(spending.getFood(), forecastMonths, annualInflationRate);
        CategoryForecast hotel       = project(spending.getHotelAndMerchant(), forecastMonths, annualInflationRate);
        CategoryForecast ent         = project(spending.getEntertainment(), forecastMonths, annualInflationRate);
        CategoryForecast travel      = project(spending.getTravel(), forecastMonths, annualInflationRate);
        CategoryForecast totalFcast  = projectTotal(spending, forecastMonths, annualInflationRate);

        double totalPotentialSavings = round(
            food.getPotentialSavings()  + hotel.getPotentialSavings() +
            ent.getPotentialSavings()   + travel.getPotentialSavings());

        return SpendingForecastResponse.builder()
            .annualInflationRate(annualInflationRate)
            .forecastMonths(forecastMonths)
            .food(food)
            .hotelAndMerchant(hotel)
            .entertainment(ent)
            .travel(travel)
            .totalSpending(totalFcast)
            .methodology("Linear Regression + Compound Inflation Projection")
            .assumptions(buildAssumptions(annualInflationRate, forecastMonths))
            .totalPotentialSavings(totalPotentialSavings)
            .build();
    }

    /** Convenience overload with default parameters. */
    public SpendingForecastResponse forecast(List<Transaction> transactions) {
        return forecast(transactions, DEFAULT_FORECAST_MONTHS, DEFAULT_INFLATION_RATE);
    }

    // ── Core projection ──────────────────────────────────────────────────────

    CategoryForecast project(CategoryDetail detail, int forecastMonths, double annualRate) {
        List<Double> history = detail.getMonthlyBreakdown().stream()
            .map(MonthlySpend::getAmount)
            .collect(Collectors.toList());

        double avg    = history.isEmpty() ? 0
            : history.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double slope  = analyticsService.linearSlope(history);
        int    histLen = history.size();

        // intercept = avg - slope * (midpoint of x indices)
        double intercept = avg - slope * ((histLen - 1) / 2.0);

        // Monthly inflation compound factor base: (1 + annualRate/100)^(1/12)
        double monthlyRate = Math.pow(1.0 + annualRate / 100.0, 1.0 / 12.0) - 1.0;

        // Start from the month after the last historical data point
        YearMonth startMonth = nextMonth(detail.getMonthlyBreakdown());

        List<MonthlyProjection> projections = new ArrayList<>();
        double conservativeTotal = 0, baselineTotal = 0, pessimisticTotal = 0;

        for (int k = 1; k <= forecastMonths; k++) {
            double inflationFactor = Math.pow(1.0 + monthlyRate, k);

            double conservative  = round(Math.max(0, avg * 0.90 * inflationFactor));
            double baseline      = round(avg * inflationFactor);
            double trendValue    = intercept + slope * (histLen + k - 1);
            double pessimistic   = round(Math.max(0, trendValue * inflationFactor));

            conservativeTotal += conservative;
            baselineTotal     += baseline;
            pessimisticTotal  += pessimistic;

            projections.add(MonthlyProjection.builder()
                .month(startMonth.plusMonths(k - 1).toString())
                .conservative(conservative)
                .baseline(baseline)
                .pessimistic(pessimistic)
                .build());
        }

        conservativeTotal = round(conservativeTotal);
        baselineTotal     = round(baselineTotal);
        pessimisticTotal  = round(pessimisticTotal);
        double potentialSavings = round(Math.max(0, baselineTotal - conservativeTotal));

        String trend = slope > 50 ? "INCREASING" : slope < -50 ? "DECREASING" : "STABLE";

        return CategoryForecast.builder()
            .categoryName(detail.getCategoryName())
            .historicalMonthlyAverage(round(avg))
            .trendSlope(round(slope))
            .trendDirection(trend)
            .projections(projections)
            .conservativeTotal(conservativeTotal)
            .baselineTotal(baselineTotal)
            .pessimisticTotal(pessimisticTotal)
            .potentialSavings(potentialSavings)
            .build();
    }

    /** Projects the aggregate of all four focus categories. */
    private CategoryForecast projectTotal(CategorySpendingResponse spending,
                                          int forecastMonths, double annualRate) {
        // Build a synthetic monthly history by summing the four groups per month
        List<MonthlySpend> foodM  = spending.getFood().getMonthlyBreakdown();
        List<MonthlySpend> hotM   = spending.getHotelAndMerchant().getMonthlyBreakdown();
        List<MonthlySpend> entM   = spending.getEntertainment().getMonthlyBreakdown();
        List<MonthlySpend> travM  = spending.getTravel().getMonthlyBreakdown();

        // Use the longest series length
        int len = Math.max(Math.max(foodM.size(), hotM.size()),
                           Math.max(entM.size(), travM.size()));

        List<Double> combined = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            double sum = getOrZero(foodM, i) + getOrZero(hotM, i)
                       + getOrZero(entM, i) + getOrZero(travM, i);
            combined.add(sum);
        }

        // Synthetic CategoryDetail (only monthly breakdown used for projection)
        CategoryDetail synthetic = CategoryDetail.builder()
            .categoryName("Total (Focus Categories)")
            .subCategories(List.of("Food", "Hotel/Merchant", "Entertainment", "Travel"))
            .monthlyBreakdown(buildSyntheticMonthly(foodM, combined))
            .totalSpend(combined.stream().mapToDouble(Double::doubleValue).sum())
            .averageMonthlySpend(combined.isEmpty() ? 0 :
                combined.stream().mapToDouble(Double::doubleValue).average().orElse(0))
            .trendDirection("STABLE")
            .build();

        return project(synthetic, forecastMonths, annualRate);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private YearMonth nextMonth(List<MonthlySpend> history) {
        if (history == null || history.isEmpty()) {
            LocalDate now = LocalDate.now();
            return YearMonth.of(now.getYear(), now.getMonthValue());
        }
        String last = history.get(history.size() - 1).getMonth();
        return YearMonth.parse(last).plusMonths(1);
    }

    private double getOrZero(List<MonthlySpend> list, int i) {
        return i < list.size() ? list.get(i).getAmount() : 0.0;
    }

    private List<MonthlySpend> buildSyntheticMonthly(List<MonthlySpend> reference,
                                                      List<Double> amounts) {
        List<MonthlySpend> result = new ArrayList<>();
        YearMonth baseMonth = reference.isEmpty()
                ? YearMonth.now()
                : YearMonth.parse(reference.get(0).getMonth());

        for (int i = 0; i < amounts.size(); i++) {
            String month = i < reference.size()
                ? reference.get(i).getMonth()
                : baseMonth.plusMonths(i).toString();
            result.add(MonthlySpend.builder().month(month).amount(round(amounts.get(i))).build());
        }
        return result;
    }

    private List<String> buildAssumptions(double inflation, int months) {
        return List.of(
            String.format("Annual inflation rate: %.1f%% (adjustable via 'inflationRate' param)", inflation),
            "Monthly compounding: rate = (1 + annualRate)^(1/12) − 1",
            "Conservative scenario: historical average reduced by 10% then inflation-adjusted",
            "Baseline scenario: historical average inflation-adjusted (no behaviour change)",
            "Pessimistic scenario: linear-regression trend extrapolated then inflation-adjusted",
            "Income growth is NOT assumed in any scenario",
            String.format("Projection horizon: %d months", months),
            "Data is based solely on the uploaded bank statement period"
        );
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
}

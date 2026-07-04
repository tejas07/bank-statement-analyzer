package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.CategoryDetail;
import com.bankanalyzer.api.dto.CategoryForecast;
import com.bankanalyzer.api.dto.MonthlyProjection;
import com.bankanalyzer.api.dto.MonthlySpend;
import com.bankanalyzer.service.analytics.CategoryLabelProvider;
import com.bankanalyzer.service.analytics.CategorySpendingCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests pinning current {@link ForecastService} behavior
 * (Phase 0 safety net). Uses a 3-month linearly-increasing history (slope
 * exactly 100/month) and 0% inflation so the compounding factor is 1,
 * making every scenario value hand-verifiable.
 */
public class ForecastServiceTest {

    private static final double DELTA = 0.01;

    private final ForecastService forecastService =
            new ForecastService(new CategorySpendingCalculator(new CategoryLabelProvider()));

    @Test
    void projectsWithZeroInflationUsingLinearHistory() {
        CategoryForecast forecast = forecastService.project(fixtureDetail(), 2, 0.0);

        assertEquals("Test Category", forecast.getCategoryName());
        assertEquals(1100.0, forecast.getHistoricalMonthlyAverage(), DELTA);
        assertEquals(100.0, forecast.getTrendSlope(), DELTA);
        assertEquals("INCREASING", forecast.getTrendDirection());

        List<MonthlyProjection> projections = forecast.getProjections();
        assertEquals(2, projections.size());

        MonthlyProjection month1 = projections.get(0);
        assertEquals("2024-04", month1.getMonth());
        assertEquals(990.0, month1.getConservative(), DELTA);
        assertEquals(1100.0, month1.getBaseline(), DELTA);
        assertEquals(1300.0, month1.getPessimistic(), DELTA);

        MonthlyProjection month2 = projections.get(1);
        assertEquals("2024-05", month2.getMonth());
        assertEquals(990.0, month2.getConservative(), DELTA);
        assertEquals(1100.0, month2.getBaseline(), DELTA);
        assertEquals(1400.0, month2.getPessimistic(), DELTA);

        assertEquals(1980.0, forecast.getConservativeTotal(), DELTA);
        assertEquals(2200.0, forecast.getBaselineTotal(), DELTA);
        assertEquals(2700.0, forecast.getPessimisticTotal(), DELTA);
        assertEquals(220.0, forecast.getPotentialSavings(), DELTA);
    }

    private CategoryDetail fixtureDetail() {
        return CategoryDetail.builder()
                .categoryName("Test Category")
                .subCategories(List.of("TEST"))
                .monthlyBreakdown(List.of(
                        MonthlySpend.builder().month("2024-01").amount(1000.0).changeFromPrevious(0).build(),
                        MonthlySpend.builder().month("2024-02").amount(1100.0).changeFromPrevious(10.0).build(),
                        MonthlySpend.builder().month("2024-03").amount(1200.0).changeFromPrevious(9.09).build()
                ))
                .build();
    }

    @Test
    void projectHandlesEmptyHistory() {
        CategoryDetail empty = CategoryDetail.builder()
                .categoryName("Empty")
                .monthlyBreakdown(List.of())
                .build();

        CategoryForecast forecast = forecastService.project(empty, 1, 6.0);

        assertEquals(0.0, forecast.getHistoricalMonthlyAverage(), DELTA);
        assertEquals(0.0, forecast.getTrendSlope(), DELTA);
        assertEquals("STABLE", forecast.getTrendDirection());
        assertEquals(1, forecast.getProjections().size());
    }
}

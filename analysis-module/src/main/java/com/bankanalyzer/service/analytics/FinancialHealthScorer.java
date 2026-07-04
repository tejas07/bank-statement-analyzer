package com.bankanalyzer.service.analytics;

import org.springframework.stereotype.Component;

/**
 * Composite 0-100 financial health score from savings rate, discretionary control, and recommendation count.
 */
@Component
public class FinancialHealthScorer {

    public int computeHealthScore(double savingsRate, double wantsPct, int recsCount) {
        int score = 0;

        // Savings rate (40 pts)
        if (savingsRate >= 30) score += 40;
        else if (savingsRate >= 20) score += 30;
        else if (savingsRate >= 10) score += 20;
        else if (savingsRate >= 0) score += 10;

        // Discretionary control (35 pts)
        if (wantsPct <= 25) score += 35;
        else if (wantsPct <= 30) score += 25;
        else if (wantsPct <= 40) score += 15;
        else if (wantsPct <= 50) score += 5;

        // Recommendation count — fewer = better (25 pts)
        if (recsCount == 0) score += 25;
        else if (recsCount <= 2) score += 15;
        else if (recsCount <= 4) score += 8;

        return Math.min(100, score);
    }

    public String healthRating(int score) {
        if (score >= 80) return "EXCELLENT";
        if (score >= 60) return "GOOD";
        if (score >= 40) return "FAIR";
        return "NEEDS_ATTENTION";
    }
}

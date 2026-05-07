package com.bankanalyzer.api.contract;

import com.bankanalyzer.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * OpenAPI / Swagger contract for {@code SpendingController}.
 * All documentation annotations live here; the controller stays clean.
 */
public interface SpendingApi {

    // ── Category spending breakdown ───────────────────────────────────────────

    @Tag(name = "Spending Analytics")
    @Operation(
        summary = "Category spending breakdown",
        description = """
            Parses the uploaded PDF and returns historical spend grouped into four focus buckets:

            | Group | Underlying categories |
            |---|---|
            | **Food & Groceries** | FOOD_DINING, GROCERIES |
            | **Hotel & Merchant** | SHOPPING |
            | **Entertainment** | ENTERTAINMENT |
            | **Travel & Fuel** | TRAVEL, FUEL |

            Each group includes: total spend, % of wallet, average monthly spend,
            month-over-month % change, trend direction (INCREASING / STABLE / DECREASING),
            chronological monthly breakdown, and top 5 merchants.
            All 14 raw categories are also returned in `allCategories`.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Category breakdown returned"),
        @ApiResponse(responseCode = "400", description = "No file or non-PDF supplied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "PDF could not be parsed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CategorySpendingResponse> getCategorySpending(
        @Parameter(description = "Bank statement PDF (max 50 MB)") MultipartFile file
    ) throws IOException;

    // ── Spending forecast ─────────────────────────────────────────────────────

    @Tag(name = "Forecast")
    @Operation(
        summary = "Inflation-adjusted spending forecast",
        description = """
            Projects future spending across Food, Hotel/Merchant, Entertainment, and Travel
            using **linear regression + compound monthly inflation**.

            Three scenarios per category per projected month:

            | Scenario | Formula |
            |---|---|
            | **Conservative** | historical avg × 0.90 × (1 + monthlyRate)^k |
            | **Baseline** | historical avg × (1 + monthlyRate)^k |
            | **Pessimistic** | regression trend × (1 + monthlyRate)^k |

            where `monthlyRate = (1 + annualRate/100)^(1/12) − 1`

            Also returns `totalPotentialSavings` — the sum saved across all categories
            if the conservative target is achieved over the full horizon.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Forecast returned"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters or missing file",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SpendingForecastResponse> getForecast(
        @Parameter(description = "Bank statement PDF (max 50 MB)") MultipartFile file,
        @Parameter(description = "Number of future months to project (1–24)", example = "6")
        int months,
        @Parameter(description = "Annual inflation rate as a percentage (0–50)", example = "6.0")
        double inflationRate
    ) throws IOException;

    // ── Productivity insights ─────────────────────────────────────────────────

    @Tag(name = "Productivity")
    @Operation(
        summary = "Financial health score and spending productivity insights",
        description = """
            Returns a composite financial health score (0–100) and actionable guidance:

            - **Health score & rating** — EXCELLENT / GOOD / FAIR / NEEDS_ATTENTION
            - **50/30/20 budget rule** — actual Needs / Wants / Savings vs benchmark,
              with ON_TARGET / OVER / UNDER status and monthly savings gap
            - **Essential vs discretionary** spend split (%)
            - **Ranked recommendations** — e.g. "Reduce Entertainment by ₹250/month → ₹3,000/year"
            - **Efficiency metrics** — daily spend, projected annual spend, emergency-fund months

            Combine with `/api/spending/forecast` to see how controlling spend today
            translates into savings over the next 6–12 months.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Productivity insights returned"),
        @ApiResponse(responseCode = "400", description = "No file or non-PDF supplied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ProductivityInsightsResponse> getProductivityInsights(
        @Parameter(description = "Bank statement PDF (max 50 MB)") MultipartFile file
    ) throws IOException;
}

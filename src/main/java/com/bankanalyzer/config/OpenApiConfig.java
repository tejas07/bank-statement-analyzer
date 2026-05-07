package com.bankanalyzer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI configuration.
 *
 * Swagger UI  : http://localhost:8080/swagger-ui.html
 * OpenAPI JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String port;

    @Bean
    public OpenAPI bankAnalyzerOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Bank Statement Analyzer API")
                .description("""
                    REST API for parsing Indian bank statement PDFs, categorizing transactions,
                    and providing spending analytics, inflation-adjusted forecasts, and
                    financial productivity insights.

                    **Upload a PDF → get instant structured JSON** with:
                    - Transaction extraction & category tagging (14 categories)
                    - Spending breakdown: Food, Hotel/Merchant, Entertainment, Travel
                    - Inflation-adjusted forecasts (3 scenarios × 6 months)
                    - Financial health score + 50/30/20 budget rule analysis
                    - Excel / PDF report download
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Bank Analyzer")
                    .email("tejas7111991@gmail.com"))
                .license(new License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server().url("http://localhost:" + port).description("Local development")))
            .tags(List.of(
                new Tag().name("Analysis")
                    .description("Parse a PDF and get JSON summaries, XLSX/PDF reports, and async job management"),
                new Tag().name("Spending Analytics")
                    .description("Category-level spend breakdown — Food, Hotel/Merchant, Entertainment, Travel"),
                new Tag().name("Forecast")
                    .description("Inflation-adjusted multi-scenario spending projections (linear regression)"),
                new Tag().name("Productivity")
                    .description("Financial health score, 50/30/20 rule analysis, and actionable recommendations"),
                new Tag().name("Health")
                    .description("Service health check")
            ));
    }
}

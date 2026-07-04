# Bank Statement Analyzer

A Spring Boot REST API that parses Indian bank statement PDFs, extracts customer and account details from the PDF
header, enriches transactions with payment mode, merchant, and category detection, and produces structured JSON
summaries, spending analytics, inflation-adjusted forecasts, financial productivity insights, and downloadable Excel/PDF
reports.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [High Level Design (HLD)](#high-level-design-hld)
- [Low Level Design (LLD)](#low-level-design-lld)
- [Flow Diagram](#flow-diagram)
- [Design Patterns](#design-patterns)
- [Features](#features)
    - [Customer Details Extraction](#customer-details-extraction)
    - [Multi-file Upload & Merge](#multi-file-upload--merge)
    - [Duplicate Transaction Detection](#duplicate-transaction-detection)
    - [Category Tagging](#category-tagging)
    - [Spending Insights](#spending-insights)
    - [Spending Analytics API](#spending-analytics-api)
    - [PDF Report Output](#pdf-report-output)
    - [Persistence (PostgreSQL)](#persistence-postgresql)
    - [Webhook / Callback](#webhook--callback)
    - [Async Processing & Job Polling](#async-processing--job-polling)
    - [File Deduplication](#file-deduplication)
- [API Reference](#api-reference)
    - [Analysis Endpoints](#analysis-endpoints)
    - [Spending Analytics Endpoints](#spending-analytics-endpoints)
- [Swagger UI](#swagger-ui)
- [Rate Limiting](#rate-limiting)
- [Caching](#caching)
- [Configuration Reference](#configuration-reference)
- [Running the App](#running-the-app)
- [Docker](#docker)
- [Adding a New Bank](#adding-a-new-bank)

---

## Tech Stack

| Layer            | Library / Framework                | Purpose                                                                                                                                         |
|------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Runtime          | Java 21, Spring Boot 3.2           | Application framework                                                                                                                           |
| PDF Parsing      | Apache PDFBox 3.0                  | Extract raw text from PDF bank statements                                                                                                       |
| Excel Generation | Apache POI 5.3 (OOXML)             | Write multi-sheet `.xlsx` reports with pie charts                                                                                               |
| PDF Generation   | OpenPDF 1.3 (LibrePDF)             | Write multi-section `.pdf` reports with tables                                                                                                  |
| Caching          | Caffeine + Spring Cache            | In-memory per-method and per-request caching (`@Cacheable`)                                                                                     |
| Rate Limiting    | Bucket4j 8.10                      | Token-bucket rate limiting per client IP                                                                                                        |
| Async Processing | Spring Kafka + `@Async`            | Background job processing with status polling                                                                                                   |
| Persistence      | Spring Data JPA + PostgreSQL       | Store uploads and transactions (optional, toggle-based)                                                                                         |
| DB Migration     | Flyway                             | Schema versioning, runs automatically on startup                                                                                                |
| API Docs         | SpringDoc OpenAPI 2.5 (Swagger UI) | Interactive API docs at `/swagger-ui.html`                                                                                                      |
| Boilerplate      | Lombok                             | `@Slf4j`, `@Builder`, `@Getter` annotations                                                                                                     |
| Build            | Maven 3.9, multi-module reactor    | 5 modules (`bank-common`, `parser-module`, `report-module`, `analysis-module`, `gateway-module`) assembled into one fat jar by `gateway-module` |
| Container        | Docker (multi-stage, Alpine)       | Lightweight production image                                                                                                                    |

---

## High Level Design (HLD)

The codebase is a **Maven multi-module reactor**. All five modules still assemble into
**one Spring Boot app** (`gateway-module` produces the runnable fat jar) — no service is
independently deployed yet. The module boundaries are drawn along the lines a future
microservices split would take: `gateway-module` → `analysis-module` → `parser-module` /
`report-module` → `bank-common`, with `bank-common` as a dependency-free shared kernel.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          Client (curl / Swagger UI)                      │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │  HTTP POST multipart/form-data
                               ▼
┌────────────────────────────── gateway-module ────────────────────────────┐
│  ┌──────────────────┐  ┌────────────────────┐  ┌──────────────────────┐ │
│  │  RateLimitFilter │─▶│ AnalyzeController  │  │  SpendingController  │ │
│  │  (Bucket4j/IP)   │  │  /api/analyze/*    │  │  /api/spending/*     │ │
│  └──────────────────┘  └────────┬───────────┘  └──────────┬───────────┘ │
│                                 │  StatementAnalysisPipeline            │
│                                 │  (parse → analyze → persist,          │
│                                 │   @Cacheable per hash)                │
└─────────────────────────────────┼──────────────────────────┼────────────┘
                                   ▼                          ▼
┌────────────────────────────── analysis-module ───────────────────────────┐
│ ┌─────────────────────┐  ┌─────────────────────┐ ┌────────────────────┐ │
│ │ TransactionAnalyzer  │  │ SpendingAnalytics   │ │ ForecastService    │ │
│ │ (impl TransactionAn- │  │ Service (facade)    │ │ - linearRegression │ │
│ │  alysis interface)   │  │  ├ CategorySpending  │ │ - inflationProject │ │
│ │ - detectPaymentMode  │  │  │   Calculator      │ └────────────────────┘ │
│ │ - extractMerchant    │  │  ├ BudgetRuleAnalyzer│                        │
│ │ - categorize (via    │  │  ├ FinancialHealth   │ ┌────────────────────┐ │
│ │   CategoryTagger)    │  │  │   Scorer          │ │ InsightService      │ │
│ └──────────────────────┘  │  └ RecommendationEng.│ │ DuplicateDetector   │ │
│                            └─────────────────────┘ │ SummaryBuilder      │ │
│ ┌──────────────────────┐  ┌─────────────────────┐ └────────────────────┘ │
│ │ AsyncJobService /    │  │ PersistenceGateway   │                        │
│ │ Kafka producer/      │  │ (NoOp / PostgreSQL)  │                        │
│ │ consumer/listener    │  └─────────────────────┘                        │
│ └──────────────────────┘  WebhookService                                 │
└──────────────┬──────────────────────────────┬────────────────────────────┘
               ▼                              ▼
┌── parser-module ───────────────┐  ┌── report-module ──────────────────────┐
│ BankStatementParser            │  │ ExcelReportGenerator (orchestrator)   │
│ (orchestrator, impl            │  │  ├ CustomerDetailsSheetWriter          │
│  StatementParsing interface)   │  │  ├ TransactionsSheetWriter             │
│ BankParserRegistry             │  │  ├ PaymentModeSheetWriter (+chart)     │
│  ├ IciciCreditCardParser       │  │  ├ MerchantSheetWriter (+chart)        │
│  ├ IciciSavingsParser          │  │  └ MonthSheetWriter (+chart)           │
│  ├ SbiParser                   │  │ PdfReportGenerator (orchestrator)      │
│  └ GenericBankParser (fallback)│  │  ├ TitleSectionWriter, ...6 more       │
└─────────────┬───────────────────┘  └──────────────┬─────────────────────┘
              ▼                                      ▼
┌────────────────────────────── bank-common ────────────────────────────────┐
│ model: Transaction, Category, PaymentMode, CustomerDetails, ParseResult   │
│ model: TransactionGroups, DuplicateTransactionFinder (shared-kernel       │
│        pure functions — grouping/totals/dedup, used by both              │
│        analysis-module and report-module without depending on each other)│
│ api.dto: SummaryResponse and all response/request DTOs                   │
│ validation: Validator<T> strategy + ForecastParams/WebhookUrlValidator    │
│ kafka: AnalysisJobEvent, AnalysisResultEvent (Kafka message contracts)    │
└────────────────────────────────────────────────────────────────────────────┘
```

**Key responsibilities:**

| Component                                              | Module      | Responsibility                                                                                                                                     |
|--------------------------------------------------------|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `RateLimitFilter`                                      | gateway     | Per-IP token bucket; blocks request with 429 before controller                                                                                     |
| `AnalyzeController`                                    | gateway     | Validates file, checks dedup, calls the pipeline, orchestrates the response                                                                        |
| `SpendingController`                                   | gateway     | Routes to analytics / forecast / productivity services                                                                                             |
| `StatementAnalysisPipeline`                            | analysis    | Single parse→analyze→persist sequence shared by the sync (`AnalyzeController`) and async (`AnalysisJobConsumer`) paths; `@Cacheable` per file hash |
| `BankStatementParser`                                  | parser      | PDFBox text extraction, sanitization, dispatches to correct parser                                                                                 |
| `BankParserRegistry`                                   | parser      | Auto-detects bank format via `supports()` in `@Order` sequence                                                                                     |
| `TransactionAnalyzer`                                  | analysis    | Enriches transactions — payment mode, merchant, category                                                                                           |
| `SpendingAnalyticsService`                             | analysis    | Facade over `CategorySpendingCalculator`, `BudgetRuleAnalyzer`, `FinancialHealthScorer`, `RecommendationEngine`                                    |
| `ForecastService`                                      | analysis    | Linear regression + inflation-adjusted 3-scenario projection                                                                                       |
| `InsightService`                                       | analysis    | Computes spending insights — highest spend, recurring, unusual                                                                                     |
| `DuplicateDetector`                                    | analysis    | Thin delegate over `bank-common`'s `DuplicateTransactionFinder`                                                                                    |
| `TransactionGroups` / `DuplicateTransactionFinder`     | bank-common | Pure, framework-free grouping/dedup shared by analysis-module and report-module                                                                    |
| `AsyncJobService` / Kafka producer, consumer, listener | analysis    | Submits analysis jobs via Kafka, tracks status, updates on result                                                                                  |
| `PersistenceGateway`                                   | analysis    | Interface — routes to real DB or no-op depending on toggle                                                                                         |
| `WebhookService`                                       | analysis    | Async HTTP POST of results to caller-provided URL                                                                                                  |
| `ExcelReportGenerator` / `PdfReportGenerator`          | report      | Orchestrate per-sheet/per-section writer classes to build `.xlsx`/`.pdf`                                                                           |

---

## Low Level Design (LLD)

### Package Structure

The repo is a 5-module Maven reactor. Package names (`com.bankanalyzer.*`) are shared
across modules — each module owns a slice of the overall package tree, not a package
of its own — so the boundary is the module (and its `pom.xml` dependencies), not the
Java package name.

```
bank-common/                            (shared kernel — see "Design Patterns" below)
└── com.bankanalyzer
    ├── model/
    │   ├── Transaction.java             Domain model — date, debit, credit, paymentMode, category
    │   ├── ParseResult.java             Parser output — transactions + bankName + statementType + customerDetails
    │   ├── CustomerDetails.java         Domain value object (parser-layer; mapped to api.dto at the API boundary)
    │   ├── PaymentMode.java             UPI, NEFT, RTGS, IMPS, ATM, CARD_POS, CHEQUE, ECS_NACH, OTHER
    │   ├── StatementType.java           SAVINGS_ACCOUNT, CURRENT_ACCOUNT, CREDIT_CARD
    │   ├── JobStatus.java               PENDING, PROCESSING, DONE, FAILED
    │   ├── Category.java                FOOD_DINING, SHOPPING, FUEL, TRAVEL, HEALTH ... (14 total)
    │   ├── TransactionGroups.java        Pure static grouping/totals (groupByPaymentMode/Merchant/Month, totalDebit/Credit)
    │   └── DuplicateTransactionFinder.java Pure static duplicate-detection algorithm
    ├── api/dto/                         20+ response/request DTOs (SummaryResponse, CategorySpendingResponse,
    │                                    ProductivityInsightsResponse, SpendingForecastResponse, DuplicateGroup, ...)
    │   └── CustomerDetailsMapper.java   Maps domain CustomerDetails → api.dto.CustomerDetails
    ├── validation/
    │   ├── Validator.java               Generic Validator<T> strategy interface
    │   ├── ForecastParams.java / ForecastParamsValidator.java
    │   └── WebhookUrlValidator.java     SSRF guard for webhook URLs
    └── kafka/
        ├── AnalysisJobEvent.java        Kafka message contract (job submission)
        └── AnalysisResultEvent.java     Kafka message contract (job result — carries SummaryResponse)

parser-module/                          depends on: bank-common
└── com.bankanalyzer.parser
    ├── BankParser.java                  Strategy interface
    ├── StatementParsing.java            Abstraction over BankStatementParser (parse/parseWithMeta/extractRawText)
    ├── AbstractBankParser.java          Template Method — shared date/amount parsing utilities
    ├── BankParserRegistry.java          Resolves the correct parser via @Order-sorted supports()
    ├── BankStatementParser.java         Orchestrator — PDF → text → ParseResult; implements StatementParsing
    └── impl/
        ├── IciciCreditCardParser.java   @Order(1) — DD-MMM-YY format
        ├── IciciSavingsParser.java      @Order(2) — DD/MM/YYYY, 3-column
        ├── SbiParser.java               @Order(3) — dual-date, dash = zero
        └── GenericBankParser.java       @Order(LOWEST) — fallback

report-module/                          depends on: bank-common
└── com.bankanalyzer.report
    ├── ExcelReportGenerator.java        Orchestrator — delegates to the writers below
    ├── excel/
    │   ├── ExcelStyleFactory.java / ExcelChartBuilder.java
    │   ├── CustomerDetailsSheetWriter.java / TransactionsSheetWriter.java
    │   └── PaymentModeSheetWriter.java / MerchantSheetWriter.java / MonthSheetWriter.java  (each + pie chart)
    ├── PdfReportGenerator.java          Orchestrator — delegates to the section writers below
    └── pdf/
        ├── PdfStyleFactory.java / ReportFormatting.java / ReportHeaderFooterEvent.java
        └── TitleSectionWriter.java / TransactionsTableWriter.java / CategorySectionWriter.java /
            PaymentModeSectionWriter.java / MonthlySectionWriter.java / DuplicatesSectionWriter.java

analysis-module/                        depends on: bank-common, parser-module, report-module
└── com.bankanalyzer
    ├── analyzer/
    │   ├── TransactionAnalysis.java     Abstraction over TransactionAnalyzer
    │   └── TransactionAnalyzer.java     Payment mode + merchant + category (@Cacheable); delegates
    │                                    grouping/totals to bank-common's TransactionGroups
    ├── service/
    │   ├── CategoryTagger.java / BKTree.java / MerchantNormalizer.java   Keyword + fuzzy-match categorization
    │   ├── InsightService.java          Highest spend, recurring, unusual transaction detection
    │   ├── DuplicateDetector.java       Thin delegate over bank-common's DuplicateTransactionFinder
    │   ├── SummaryBuilder.java          Shared buildSummary logic (sync + async paths)
    │   ├── SpendingAnalyticsService.java  Facade — see analytics/ below
    │   ├── ForecastService.java         Linear regression + inflation projection (3 scenarios)
    │   ├── AsyncJobService.java / TempFileStore.java   In-memory job store bridging upload → Kafka consumer
    │   ├── PersistenceGateway.java / NoOpPersistenceGateway.java / StatementPersistenceService.java
    │   ├── WebhookService.java          Async HTTP POST with 3-retry + SSRF guard
    │   └── analytics/                   Phase 1.2 split of the former 501-line SpendingAnalyticsService
    │       ├── CategoryGroupDefinitions.java   Static category-group config
    │       ├── CategoryLabelProvider.java      Table-driven display labels
    │       ├── CategorySpendingCalculator.java Category spend breakdown + trend (linearSlope)
    │       ├── BudgetRuleAnalyzer.java         50/30/20 computation
    │       ├── FinancialHealthScorer.java      0-100 composite score
    │       ├── RecommendationEngine.java       Table-driven per-category benchmarks
    │       ├── ProductivityInsightsBuilder.java Orchestrates the above
    │       └── TransactionMath.java            Shared round/debits/distinctMonths helpers
    ├── pipeline/
    │   ├── StatementAnalysisPipeline.java      parse → analyze → persist, shared by sync + async paths
    │   ├── DefaultStatementAnalysisPipeline.java  + @Cacheable summary/XLSX/PDF methods keyed by file hash
    │   └── ParsedStatement.java         Holder: ParseResult + enriched transactions + uploadId
    ├── kafka/
    │   ├── AnalysisJobConsumer.java / AnalysisJobProducer.java / AnalysisResultListener.java
    ├── model/entity/                    JPA: StatementUploadEntity, TransactionEntity
    ├── repository/                      StatementUploadRepository, TransactionRepository
    ├── aspect/AnalysisAspect.java       AOP timing/audit logging on service..* methods
    └── config/
        ├── KafkaConfig.java / PersistenceConfig.java / DedupProperties.java / AsyncConfig.java

gateway-module/                         depends on: bank-common, parser-module, report-module, analysis-module
└── com.bankanalyzer
    ├── BankAnalyzerApplication.java     Spring Boot entry point (the only runnable module)
    ├── api/
    │   ├── AnalyzeController.java       REST endpoints (single, multi, async, pdf-report)
    │   ├── SpendingController.java      REST endpoints (categories, forecast, productivity)
    │   ├── GlobalExceptionHandler.java  @ControllerAdvice — 4xx/5xx handling
    │   └── contract/                    AnalyzeApi / SpendingApi — Swagger annotations, kept off the controllers
    ├── validation/
    │   └── FileUploadValidator.java / MultiFileUploadValidator.java   (need spring-web's MultipartFile,
    │                                                                   so they live here, not in bank-common)
    ├── filter/RateLimitFilter.java      OncePerRequestFilter — Bucket4j token bucket per IP
    └── config/
        ├── CacheConfig.java             Caffeine: paymentMode, merchant, category, analysis caches
        ├── CorsConfig.java / OpenApiConfig.java / RateLimitProperties.java
```

### Class Relationships

```
BankParser (interface)
    └── AbstractBankParser (abstract — shared utilities)
            ├── IciciCreditCardParser
            ├── IciciSavingsParser
            ├── SbiParser
            └── GenericBankParser (fallback)
StatementParsing (interface) ◀── implemented by ── BankStatementParser (uses BankParserRegistry)

TransactionAnalysis (interface) ◀── implemented by ── TransactionAnalyzer
    TransactionAnalyzer.groupByX()/totalX() ──▶ delegates to bank-common's TransactionGroups (static)

PersistenceGateway (interface)
    ├── NoOpPersistenceGateway      @ConditionalOnProperty(persistence.enabled=false)
    └── StatementPersistenceService @ConditionalOnProperty(persistence.enabled=true)

SpendingAnalyticsService (facade)
    ├──▶ CategorySpendingCalculator ──▶ CategoryLabelProvider
    └──▶ ProductivityInsightsBuilder ──▶ CategorySpendingCalculator, BudgetRuleAnalyzer,
                                         FinancialHealthScorer, RecommendationEngine

ForecastService ──▶ CategorySpendingCalculator   (not the SpendingAnalyticsService facade —
                                                   forecasting only needs spend calculation)

StatementAnalysisPipeline (interface) ◀── implemented by ── DefaultStatementAnalysisPipeline
    ──▶ StatementParsing, TransactionAnalysis, SummaryBuilder, PersistenceGateway,
        ExcelReportGenerator, PdfReportGenerator (for its @Cacheable report-building methods)

AnalyzeController ──▶ StatementAnalysisPipeline   (sync path)
AnalysisJobConsumer ──▶ StatementAnalysisPipeline (async/Kafka path — same pipeline, no duplication)

ExcelReportGenerator / PdfReportGenerator
    ──▶ bank-common's TransactionGroups / DuplicateTransactionFinder directly (no dependency
        on analyzer/service — this is what keeps report-module and analysis-module acyclic)

SummaryBuilder ──▶ TransactionAnalysis, InsightService, DuplicateDetector
```

### Transaction Model

```
Transaction
├── date          : LocalDate
├── description   : String
├── debit         : double
├── credit        : double
├── balance       : double
├── paymentMode   : PaymentMode   (set by TransactionAnalyzer)
├── merchantName  : String        (set by TransactionAnalyzer)
└── category      : Category      (set by CategoryTagger)
```

### Supported Bank Formats

| Parser                  | Statement Type  | Date Format                               | Detection Keywords            | Customer Fields                                                             |
|-------------------------|-----------------|-------------------------------------------|-------------------------------|-----------------------------------------------------------------------------|
| `IciciCreditCardParser` | Credit Card     | `DD-MMM-YY`                               | `ICICI` + `Credit Card`       | Name, account, statement period, closing balance                            |
| `IciciSavingsParser`    | Savings Account | `DD/MM/YYYY`                              | `ICICI` (no CC keyword)       | Name, account, branch, IFSC, statement period                               |
| `SbiParser`             | Savings Account | `DD/MM/YYYY DD/MM/YYYY`                   | `State Bank of India`, `SBIN` | 19 fields — name, account, branch, IFSC, MICR, CIF, PAN, email, mobile, KYC |
| `GenericBankParser`     | Savings Account | `DD/MM/YYYY`, `DD-MM-YYYY`, `DD MMM YYYY` | fallback — always matches     | None                                                                        |

---

## Flow Diagram

```
POST /api/analyze/summary
         │
         ▼
┌─────────────────────┐
│   RateLimitFilter   │──── 429 Too Many Requests
└────────┬────────────┘
         ▼
┌─────────────────────┐
│  AnalyzeController  │─── MD5 hash of file bytes
└────────┬────────────┘
         ▼
┌─────────────────────┐     ┌──────────────────────────┐
│  Dedup check        │─HIT▶│  409 Conflict            │
└────────┬────────────┘     └──────────────────────────┘
         ▼
┌──────────────────────────────────────────────────────┐
│  pipeline.buildSummaryCached(hash, bytes, filename)   │──── @Cacheable("analysis", key=hash+":summary")
│  (StatementAnalysisPipeline, analysis-module)         │     HIT → cached SummaryResponse, no re-work
└────────┬───────────────────────────────────────────────┘
         │ MISS — pipeline runs:
         ▼
┌─────────────────────┐
│ BankStatementParser │──── PDFBox extract → sanitize → ParseResult   (parser-module)
└────────┬────────────┘
         ▼
┌─────────────────────┐
│  TransactionAnalyzer│──── paymentMode, merchant, category (@Cacheable)   (analysis-module)
└────────┬────────────┘
         ▼
┌─────────────────────┐
│  PersistenceGateway │──── NoOp (disabled) or PostgreSQL (enabled)
└────────┬────────────┘
         ▼
┌─────────────────────┐
│  SummaryBuilder      │──── groups, InsightService, DuplicateDetector → SummaryResponse
└────────┬────────────┘
         ▼
     JSON / XLSX / PDF response    +    WebhookService.notify() [async]

The XLSX/PDF report endpoints follow the same pipeline shape via
pipeline.buildExcelReportCached(...) / buildPdfReportCached(...) — each cached
independently under "<hash>:report" / "<hash>:pdf", and each internally calling
ExcelReportGenerator / PdfReportGenerator (report-module) once parsing+persistence
is done. The async path (/api/analyze/submit → Kafka → AnalysisJobConsumer) calls
the SAME pipeline.analyzeAndPersist(...) — no separate copy of this sequence exists.


POST /api/spending/categories  (or /forecast or /productivity)
         │
         ▼
┌─────────────────────┐
│  SpendingController │
└────────┬────────────┘
         ▼
┌───────────────────────────────┐
│  BankStatementParser          │──── same PDF parsing pipeline
│  TransactionAnalyzer          │
└────────┬──────────────────────┘
         ▼
┌─────────────────────────────────────────────────────┐
│  SpendingAnalyticsService (facade)                  │
│  - CategorySpendingCalculator: group by FOOD/HOTEL/ │
│    ENTERTAINMENT/TRAVEL, monthly breakdown + slope  │
│  - BudgetRuleAnalyzer: 50/30/20 rule                │
│  - FinancialHealthScorer + RecommendationEngine     │
└────────┬────────────────────────────────────────────┘
         │  (only for /forecast)
         ▼
┌─────────────────────────────────────────────────────┐
│  ForecastService ──▶ CategorySpendingCalculator      │
│  - inflation compound: (1 + r)^(1/12) − 1          │
│  - conservative = avg × 0.9 × inflationFactor^k    │
│  - baseline    = avg × inflationFactor^k            │
│  - pessimistic = regression trend × inflationFactor^k│
└─────────────────────────────────────────────────────┘
```

---

## Design Patterns

### Strategy + Registry (Parser)

`BankParser` is a strategy interface. `BankParserRegistry` resolves the correct implementation via `@Order`-sorted
`supports()` calls.

### Template Method (Abstract Parser)

`AbstractBankParser` provides shared utilities. Concrete parsers only implement `supports()`, `parse()`, and
`bankName()`.

### Strategy (Persistence Toggle)

`PersistenceGateway` interface with two implementations selected by `@ConditionalOnProperty`.

```
persistence.enabled=false  →  NoOpPersistenceGateway    (default)
persistence.enabled=true   →  StatementPersistenceService (PostgreSQL)
```

### Strategy (Validation)

`Validator<T>` is a generic strategy interface (`validate(T target)`, throws `IllegalArgumentException` on failure,
mapped to HTTP 400 by `GlobalExceptionHandler`). Implementations: `FileUploadValidator`, `MultiFileUploadValidator`,
`ForecastParamsValidator`, `WebhookUrlValidator` — replacing what used to be duplicated inline `if`/`throw` blocks in
each controller.

### Dependency Inversion (Interface Segregation)

`TransactionAnalysis` and `StatementParsing` are extracted from `TransactionAnalyzer`/`BankStatementParser` so
controllers, report generators, and the pipeline depend on the abstraction, not the concrete class. This is what makes
swapping the concrete implementation for a remote-service client (a future microservices step) a mechanical change
rather than a rewrite.

### Facade

`SpendingAnalyticsService` is a thin facade (~30 lines) over `CategorySpendingCalculator`, `BudgetRuleAnalyzer`,
`FinancialHealthScorer`, and `RecommendationEngine` — the original 501-line class split by single responsibility, with
the facade kept so `SpendingController`/`ForecastService` didn't need to change. `ExcelReportGenerator`/
`PdfReportGenerator` are themselves facades over their per-sheet/per-section writer classes.

### Shared Kernel (bank-common)

`TransactionGroups` and `DuplicateTransactionFinder` are pure, static, framework-free functions over `Transaction`
living in `bank-common`. Both `analysis-module` (`TransactionAnalyzer`, `DuplicateDetector`) and `report-module` (the
sheet/section writers) call them directly — this is what keeps the report ↔ analysis module dependency acyclic:
report-module never needs to depend on analyzer/service just to compute a total or find a duplicate.

### Pipeline (Unified Orchestration)

`StatementAnalysisPipeline` is the single parse → analyze → persist sequence used by both the synchronous REST path (
`AnalyzeController`) and the async Kafka path (`AnalysisJobConsumer`) — previously duplicated inline in both places. Its
`@Cacheable` methods (`buildSummaryCached`, `buildExcelReportCached`, `buildPdfReportCached`) also replaced
`AnalyzeController`'s manual `CacheManager.get/put` calls.

### Table-Driven Config (over switch statements)

`CategoryLabelProvider` (category → display label) and `RecommendationEngine`'s per-category benchmark map (target % +
tip) replace what used to be `switch` statements — adding a category no longer means touching multiple methods.

### Filter Chain (Rate Limiting)

`RateLimitFilter` extends `OncePerRequestFilter` and runs before any controller logic.

### Cache-Aside

| Level    | Where                                                                                              | Key                                               |
|----------|----------------------------------------------------------------------------------------------------|---------------------------------------------------|
| Method   | `detectPaymentMode`, `extractMerchant`, `categorize`                                               | description string                                |
| Pipeline | `StatementAnalysisPipeline.buildSummaryCached` / `buildExcelReportCached` / `buildPdfReportCached` | `<MD5 of PDF bytes>:summary` / `:report` / `:pdf` |

---

## Features

### Customer Details Extraction

Every `/api/analyze/summary` response includes a `customerDetails` object.

**SBI — 19 extracted fields:**

| Field                               | Source                               |
|-------------------------------------|--------------------------------------|
| `customerName`                      | Title-prefixed name                  |
| `accountNumber`                     | `Account Number : <digits>`          |
| `branch` / `branchCode`             | `Branch Name / Code`                 |
| `ifscCode` / `micrCode`             | `IFSC Code / MICR Code`              |
| `cifNumber`                         | `CIF Number`                         |
| `email` / `mobile`                  | `Email ID / Mobile Number`           |
| `pan`                               | `PAN <XXXXXNNNNX>`                   |
| `kycStatus` / `segment`             | `KYC Status / Segment`               |
| `accountStatus` / `accountOpenDate` | `Account Status / Account open Date` |
| `statementPeriod`                   | `Statement From : <date> to <date>`  |
| `closingBalance` / `currency`       | `Clear Balance / Currency`           |
| `nomineeNam`                        | `Nominee Name`                       |

---

### Multi-file Upload & Merge

Upload up to **10 PDF statements** at once. Transactions are merged, sorted chronologically, and returned as a single
summary.

```bash
curl -F "files=@sbi_jan.pdf" -F "files=@icici_jan.pdf" \
  http://localhost:8080/api/analyze/multi/summary
```

---

### Duplicate Transaction Detection

Groups transactions by `normalized_description | debit | credit`. Catches double-billing across days.

```json
"duplicates": [
  { "description": "UPI/Swiggy/Order", "debit": 349.00, "count": 3,
    "occurrenceDates": ["2024-03-10", "2024-03-10", "2024-03-11"] }
]
```

---

### Category Tagging

14 categories with 100+ keywords matched against transaction descriptions.

| Category        | Sample Keywords                                   |
|-----------------|---------------------------------------------------|
| `FOOD_DINING`   | Swiggy, Zomato, McDonald, Cafe, Restaurant, Hotel |
| `TRAVEL`        | IRCTC, Ola, Uber, MakeMyTrip, IndiGo, Rapido      |
| `SHOPPING`      | Amazon, Flipkart, Myntra, Meesho, Nykaa           |
| `FUEL`          | HPCL, IOCL, BPCL, Reliance BP, Nayara             |
| `UTILITIES`     | Airtel, Jio, Electricity, Broadband, DTH          |
| `INVESTMENT`    | Zerodha, Groww, Mutual Fund, SIP, Kuvera          |
| `ENTERTAINMENT` | Netflix, Spotify, BookMyShow, PVR, Steam          |
| `HEALTH`        | Apollo, MedPlus, Practo, PharmEasy, Hospital      |

---

### Spending Insights

Included in every `/api/analyze/summary` under `insights`:

```json
"insights": {
  "highestSpendDay": "2024-03-15",
  "highestSpendDayAmount": 5430.00,
  "highestSpendMonth": "2024-03",
  "highestSpendMonthAmount": 45230.00,
  "averageMonthlySpend": 32000.00,
  "recurringTransactions": [
    { "merchantName": "Swiggy", "occurrences": 8, "averageAmount": 320.00 }
  ],
  "unusualTransactions": [
    { "date": "2024-03-15", "merchantName": "Amazon", "amount": 15000.00 }
  ]
}
```

**Detection:** recurring = ≥2 occurrences by merchant; unusual = debits above mean + 2σ.

---

### Spending Analytics API

Three new endpoints under `/api/spending/` provide category-level spend data, future projections, and productivity
recommendations.

#### Category Groups

| Group            | Underlying Categories      |
|------------------|----------------------------|
| Food & Groceries | `FOOD_DINING`, `GROCERIES` |
| Hotel & Merchant | `SHOPPING`                 |
| Entertainment    | `ENTERTAINMENT`            |
| Travel & Fuel    | `TRAVEL`, `FUEL`           |

#### Forecast Methodology

```
Monthly inflation rate:  r_m = (1 + annualRate/100)^(1/12) − 1

Conservative (k):   avg × 0.90 × (1 + r_m)^k     ← spending-control goal
Baseline (k):       avg        × (1 + r_m)^k     ← no behaviour change
Pessimistic (k):    trend_k    × (1 + r_m)^k     ← regression extrapolation

where trend_k = intercept + slope × (histLen + k − 1)
```

#### Financial Health Score (0–100)

| Dimension             | Max pts | Criteria                                  |
|-----------------------|---------|-------------------------------------------|
| Savings rate          | 40      | ≥30% = 40, ≥20% = 30, ≥10% = 20, ≥0% = 10 |
| Discretionary control | 35      | Wants ≤25% = 35, ≤30% = 25, ≤40% = 15     |
| Recommendation count  | 25      | 0 recs = 25, ≤2 = 15, ≤4 = 8              |

Ratings: **EXCELLENT** ≥80 / **GOOD** ≥60 / **FAIR** ≥40 / **NEEDS_ATTENTION** <40

#### 50/30/20 Budget Rule

| Bucket  | Categories                                              | Target |
|---------|---------------------------------------------------------|--------|
| Needs   | Utilities, EMI/Loans, Groceries, Health, Fuel           | 50%    |
| Wants   | Food/Dining, Entertainment, Shopping, Travel, Education | 30%    |
| Savings | Investment, SIP                                         | 20%    |

---

### PDF Report Output

```bash
curl -F "file=@statement.pdf" \
  http://localhost:8080/api/analyze/pdf-report --output report.pdf
```

Sections: Summary bar → Customer details → All transactions → By category → By payment mode → Monthly breakdown →
Duplicates (if any).

---

### Async Processing & Job Polling

```bash
# Submit
curl -F "file=@statement.pdf" http://localhost:8080/api/analyze/submit
# → { "jobId": "a3f4c7b2-...", "statusUrl": "http://localhost:8080/api/analyze/status/a3f4c7b2-..." }

# Poll
curl http://localhost:8080/api/analyze/status/a3f4c7b2-...
```

| Status       | HTTP | Meaning                            |
|--------------|------|------------------------------------|
| `PENDING`    | 202  | Queued                             |
| `PROCESSING` | 202  | In progress                        |
| `DONE`       | 200  | Full `SummaryResponse` in `result` |
| `FAILED`     | 500  | Error in `error` field             |

Jobs auto-purged after **1 hour** via `@Scheduled` (runs every 5 min).

---

### Persistence (PostgreSQL)

Disabled by default.

```properties
# Enable
persistence.enabled=true
spring.datasource.url=jdbc:postgresql://localhost:5432/bankanalyzer
spring.datasource.username=postgres
spring.datasource.password=postgres
```

Schema (auto-created by Flyway):

```sql
statement_uploads  — id, file_hash, original_filename, bank_name, transaction_count, uploaded_at
transactions       — id, upload_id, txn_date, description, debit, credit, payment_mode, category
```

---

### Webhook / Callback

```bash
curl -F "file=@statement.pdf" \
  "http://localhost:8080/api/analyze/summary?webhookUrl=https://your-server.com/callback"
```

- Async (`@Async`), does not block the HTTP response
- 3 retries with exponential backoff (1s, 2s, 3s)
- SSRF guard: blocks `localhost`, `127.x`, `10.x`, `192.168.x`, `172.16–31.x`

---

### File Deduplication

When persistence is enabled, the same PDF cannot be submitted twice within the configured window.

```properties
dedup.enabled=true
dedup.window-hours=24
```

Returns `HTTP 409 Conflict` for duplicate uploads.

---

## API Reference

### Analysis Endpoints

| Method | Endpoint                      | Returns | Description                           |
|--------|-------------------------------|---------|---------------------------------------|
| `GET`  | `/api/health`                 | JSON    | Service health check                  |
| `POST` | `/api/analyze/summary`        | JSON    | Full analysis of a single PDF         |
| `POST` | `/api/analyze/report`         | XLSX    | 5-sheet Excel report                  |
| `POST` | `/api/analyze/pdf-report`     | PDF     | Formatted PDF report                  |
| `POST` | `/api/analyze/raw-text`       | Text    | Raw PDFBox output (debug)             |
| `POST` | `/api/analyze/multi/summary`  | JSON    | Merged analysis of up to 10 PDFs      |
| `POST` | `/api/analyze/multi/report`   | XLSX    | Merged Excel report                   |
| `POST` | `/api/analyze/submit`         | JSON    | Submit PDF for async processing (202) |
| `GET`  | `/api/analyze/status/{jobId}` | JSON    | Poll async job status                 |

#### `POST /api/analyze/summary` — Example response

```json
{
  "uploadId": 7,
  "detectedBank": "ICICI Credit Card",
  "statementType": "CREDIT_CARD",
  "totalTransactions": 42,
  "totalDebit": 85000.00,
  "totalCredit": 120000.00,
  "byPaymentMode": [
    { "mode": "UPI", "count": 18, "totalDebit": 32000.00, "totalCredit": 0.0 }
  ],
  "byMerchant": [
    { "merchant": "Swiggy", "count": 5, "totalDebit": 1850.00 }
  ],
  "byMonth": [
    { "month": "2024-04", "debitCount": 12, "totalDebit": 28000.00,
      "creditCount": 2, "totalCredit": 5000.00 }
  ],
  "insights": { ... },
  "customerDetails": { "customerName": "Mr. Tejas Gowda", "pan": "ABCDE1234F", ... }
}
```

---

### Spending Analytics Endpoints

| Method | Endpoint                     | Returns | Description                                         |
|--------|------------------------------|---------|-----------------------------------------------------|
| `POST` | `/api/spending/categories`   | JSON    | Spend breakdown: Food, Hotel, Entertainment, Travel |
| `POST` | `/api/spending/forecast`     | JSON    | Inflation-adjusted 3-scenario projections           |
| `POST` | `/api/spending/productivity` | JSON    | Health score + 50/30/20 + recommendations           |

All three endpoints accept `multipart/form-data` with a single `file` field (PDF, max 50 MB).

#### `POST /api/spending/categories` — Example response

```json
{
  "totalSpend": 55000.00,
  "totalMonths": 4,
  "dateRange": "2024-01 to 2024-04",
  "food": {
    "categoryName": "Food & Groceries",
    "subCategories": ["FOOD_DINING", "GROCERIES"],
    "totalSpend": 14200.00,
    "percentageOfTotal": 25.82,
    "averageMonthlySpend": 3550.00,
    "momChangePercent": 6.5,
    "trendDirection": "INCREASING",
    "highestSpend": 4100.00,
    "highestSpendMonth": "2024-04",
    "monthlyBreakdown": [
      { "month": "2024-01", "amount": 3200.00, "changeFromPrevious": 0.0 },
      { "month": "2024-02", "amount": 3400.00, "changeFromPrevious": 6.25 }
    ],
    "topMerchants": [
      { "merchant": "Swiggy", "count": 12, "totalDebit": 4800.00 }
    ]
  },
  "hotelAndMerchant": { ... },
  "entertainment": { ... },
  "travel": { ... },
  "allCategories": [ ... ]
}
```

#### `POST /api/spending/forecast?months=6&inflationRate=6.0` — Example response

```json
{
  "forecastMonths": 6,
  "annualInflationRate": 6.0,
  "methodology": "Linear Regression + Compound Inflation Projection",
  "totalPotentialSavings": 2840.00,
  "food": {
    "categoryName": "Food & Groceries",
    "historicalMonthlyAverage": 3550.00,
    "trendSlope": 120.50,
    "trendDirection": "INCREASING",
    "conservativeTotal": 19380.00,
    "baselineTotal": 21540.00,
    "pessimisticTotal": 25100.00,
    "potentialSavings": 2160.00,
    "projections": [
      { "month": "2024-05", "conservative": 3195.00, "baseline": 3552.00, "pessimistic": 3850.00 },
      { "month": "2024-06", "conservative": 3211.00, "baseline": 3570.00, "pessimistic": 3980.00 }
    ]
  },
  "assumptions": [
    "Annual inflation rate: 6.0% (adjustable via 'inflationRate' param)",
    "Conservative scenario: historical average reduced by 10% then inflation-adjusted",
    "Baseline scenario: historical average inflation-adjusted (no behaviour change)",
    "Pessimistic scenario: linear-regression trend extrapolated then inflation-adjusted"
  ]
}
```

#### `POST /api/spending/productivity` — Example response

```json
{
  "financialHealthScore": 68,
  "healthRating": "GOOD",
  "totalIncome": 80000.00,
  "totalSpend": 55000.00,
  "netSavings": 25000.00,
  "savingsRate": 31.25,
  "budgetRuleAnalysis": {
    "needsTargetPercent": 50.0,
    "wantsTargetPercent": 30.0,
    "savingsTargetPercent": 20.0,
    "needsActualPercent": 44.0,
    "wantsActualPercent": 37.5,
    "savingsActualPercent": 31.25,
    "needsStatus": "ON_TARGET",
    "wantsStatus": "OVER",
    "savingsStatus": "ON_TARGET",
    "savingsGapMonthly": 0.0
  },
  "essentialSpend": 24200.00,
  "discretionarySpend": 20600.00,
  "essentialPercent": 44.0,
  "discretionaryPercent": 37.5,
  "recommendations": [
    {
      "priority": 1,
      "category": "Entertainment",
      "action": "REDUCE",
      "message": "Entertainment accounts for 9.5% of your spend (benchmark: 8%). Reducing by ₹275/month could save ₹3,300/year. Tip: audit streaming subscriptions — cancel unused ones and share family plans.",
      "currentMonthlyAmount": 800.00,
      "targetMonthlyAmount": 525.00,
      "potentialMonthlySavings": 275.00,
      "annualSavingsPotential": 3300.00
    }
  ],
  "averageDailySpend": 612.50,
  "projectedAnnualSpend": 223562.50,
  "emergencyFundMonths": 1.82
}
```

---

## Swagger UI

Interactive API documentation is available when the app is running.

| URL                                      | Content                                   |
|------------------------------------------|-------------------------------------------|
| `http://localhost:8080/swagger-ui.html`  | Swagger UI — try endpoints in the browser |
| `http://localhost:8080/v3/api-docs`      | Raw OpenAPI 3.0 JSON spec                 |
| `http://localhost:8080/v3/api-docs.yaml` | Raw OpenAPI 3.0 YAML spec                 |

The UI organises endpoints into four groups:

| Tag                    | Endpoints                                                       |
|------------------------|-----------------------------------------------------------------|
| **Analysis**           | `/api/analyze/*` — summary, report, PDF, multi, async, raw-text |
| **Spending Analytics** | `/api/spending/categories`                                      |
| **Forecast**           | `/api/spending/forecast`                                        |
| **Productivity**       | `/api/spending/productivity`                                    |
| **Health**             | `/api/health`                                                   |

Each endpoint documents its request parameters, file upload field, all response codes, and example response schemas — no
separate Postman collection needed.

---

## Rate Limiting

Applied to all `/api/*` endpoints. Health check is never rate-limited.

```properties
ratelimit.enabled=true
ratelimit.capacity=20
ratelimit.refill-tokens=20
ratelimit.refill-duration=1
ratelimit.refill-unit=MINUTES
ratelimit.cache-size=10000
ratelimit.cache-expire-minutes=10
```

`HTTP 429` response includes `Retry-After`, `X-Rate-Limit-Capacity`, and `X-Rate-Limit-Remaining` headers.

---

## Caching

| Cache         | Key              | TTL    | Purpose                               |
|---------------|------------------|--------|---------------------------------------|
| `paymentMode` | description      | none   | `detectPaymentMode()` — pure function |
| `merchant`    | description      | none   | `extractMerchant()` — pure function   |
| `category`    | description      | none   | `categorize()` — pure function        |
| `analysis`    | MD5 of PDF bytes | 1 hour | Full parse + analyze result per file  |

All caches use **Caffeine** (in-memory). Spending Analytics endpoints do not use the cache (real-time per request).

---

## Configuration Reference

```properties
# ── Server ─────────────────────────────────────────────────────────────────
server.port=8080
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=52MB

# ── Persistence toggle ──────────────────────────────────────────────────────
persistence.enabled=false

# ── PostgreSQL (only when persistence.enabled=true) ────────────────────────
#spring.datasource.url=jdbc:postgresql://localhost:5432/bankanalyzer
#spring.datasource.username=postgres
#spring.datasource.password=postgres

# ── Deduplication ───────────────────────────────────────────────────────────
dedup.enabled=true
dedup.window-hours=24

# ── Rate limiting ───────────────────────────────────────────────────────────
ratelimit.enabled=true
ratelimit.capacity=20
ratelimit.refill-tokens=20
ratelimit.refill-duration=1
ratelimit.refill-unit=MINUTES
ratelimit.cache-size=10000
ratelimit.cache-expire-minutes=10

# ── Swagger ─────────────────────────────────────────────────────────────────
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/v3/api-docs

# ── Logging ─────────────────────────────────────────────────────────────────
logging.level.com.bankanalyzer=INFO
```

---

## Running the App

**Prerequisites:** Java 21+, Maven 3.9+

This is a Maven multi-module reactor (`bank-common`, `parser-module`, `report-module`,
`analysis-module`, `gateway-module`) that still builds and runs as ONE Spring Boot app —
`gateway-module` assembles the other four into a single runnable JAR.

```bash
# Build (from the repo root — builds all 5 modules in dependency order)
mvn clean package -DskipTests

# Run (no DB needed by default) — the runnable jar lives under gateway-module/target
java -jar gateway-module/target/bank-statement-analyzer-1.3.0.jar

# Open Swagger UI
open http://localhost:8080/swagger-ui.html

# Health check
curl http://localhost:8080/api/health

# Quick spending test
curl -F "file=@statement.pdf" http://localhost:8080/api/spending/categories | jq .
```

---

## Docker

```bash
# Build image
docker build -t bank-statement-analyzer:latest .

# Run
docker run -p 8080:8080 bank-statement-analyzer:latest

# With persistence
docker run -p 8080:8080 \
  -e PERSISTENCE_ENABLED=true \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/bankanalyzer \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  bank-statement-analyzer:latest
```

**Dockerfile:** two-stage build — Maven 3.9 compiles, Eclipse Temurin 21 JRE runs. Final image ~180 MB.

---

## Adding a New Bank

1. Create `parser-module/src/main/java/com/bankanalyzer/parser/impl/XyzBankParser.java`
2. Extend `AbstractBankParser`, implement `BankParser`
3. Annotate `@Component` + `@Order(N)`

```java
@Slf4j
@Component
@Order(4)
public class XyzBankParser extends AbstractBankParser {

    @Override public String bankName()             { return "XYZ Bank"; }
    @Override public StatementType statementType() { return StatementType.SAVINGS_ACCOUNT; }

    @Override
    public boolean supports(String rawText) {
        return rawText.contains("XYZ Bank");
    }

    @Override
    public List<Transaction> parse(String text) {
        // line-by-line regex — use buildDebitCreditTransaction() from AbstractBankParser
    }

    @Override
    public CustomerDetails extractCustomerDetails(String rawText) {
        return CustomerDetails.builder()
            .accountNumber(/* regex */)
            .customerName(/* regex */)
            .build();
    }
}
```

No other files need to change — `BankParserRegistry` picks it up automatically via Spring DI.

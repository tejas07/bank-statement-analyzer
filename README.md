# Bank Statement Analyzer

A Spring Boot REST API that parses Indian bank statement PDFs, extracts customer and account details from the PDF header, enriches transactions with payment mode, merchant, and category detection, and produces structured JSON summaries or downloadable Excel/PDF reports.

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
  - [PDF Report Output](#pdf-report-output)
  - [Persistence (PostgreSQL)](#persistence-postgresql)
  - [Webhook / Callback](#webhook--callback)
  - [Async Processing & Job Polling](#async-processing--job-polling)
  - [File Deduplication](#file-deduplication)
- [API Reference](#api-reference)
- [Rate Limiting](#rate-limiting)
- [Caching](#caching)
- [Configuration Reference](#configuration-reference)
- [Running the App](#running-the-app)
- [Docker](#docker)
- [Adding a New Bank](#adding-a-new-bank)

---

## Tech Stack

| Layer | Library / Framework | Purpose |
|---|---|---|
| Runtime | Java 17, Spring Boot 3.2 | Application framework |
| PDF Parsing | Apache PDFBox 3.0 | Extract raw text from PDF bank statements |
| Excel Generation | Apache POI 5.3 (OOXML) | Write multi-sheet `.xlsx` reports with pie charts |
| PDF Generation | OpenPDF 1.3 (LibrePDF) | Write multi-section `.pdf` reports with tables |
| Caching | Caffeine + Spring Cache | In-memory per-method and per-request caching |
| Rate Limiting | Bucket4j 8.10 | Token-bucket rate limiting per client IP |
| Async Processing | `CompletableFuture` + `@Async` | Background job processing with status polling |
| Persistence | Spring Data JPA + PostgreSQL | Store uploads and transactions (optional, toggle-based) |
| DB Migration | Flyway | Schema versioning, runs automatically on startup |
| Boilerplate | Lombok | `@Slf4j`, `@Builder`, `@Data` annotations |
| Build | Maven 3.9 | Dependency management, fat jar packaging |
| Container | Docker (multi-stage, Alpine) | Lightweight production image (~180 MB) |

---

## High Level Design (HLD)

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          Client (curl / UI)                              │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │  HTTP POST multipart/form-data
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                        Spring Boot Application                           │
│                                                                          │
│  ┌──────────────────┐    ┌────────────────────┐    ┌─────────────────┐  │
│  │  RateLimitFilter │───▶│  AnalyzeController │───▶│  Cache          │  │
│  │  (Bucket4j/IP)   │    │  /api/analyze/*    │    │  (Caffeine)     │  │
│  └──────────────────┘    └─────────┬──────────┘    └─────────────────┘  │
│                                    │                                     │
│          ┌─────────────────────────┼────────────────────────┐           │
│          ▼                         ▼                        ▼           │
│ ┌──────────────────┐  ┌──────────────────────┐  ┌──────────────────┐   │
│ │ BankStatement    │  │  TransactionAnalyzer  │  │  InsightService  │   │
│ │ Parser           │  │  - detectPaymentMode  │  │  - highestSpend  │   │
│ │ (Orchestrator)   │  │  - extractMerchant    │  │  - recurring     │   │
│ └────────┬─────────┘  │  - categorize         │  │  - unusual txns  │   │
│          │            └──────────────────────┘  └──────────────────┘   │
│ ┌────────▼─────────┐                                                    │
│ │ BankParser       │  ┌──────────────────────┐  ┌──────────────────┐   │
│ │ Registry         │  │  DuplicateDetector   │  │  SummaryBuilder  │   │
│ │ (auto-detect)    │  │  (Feature 3)         │  │  (shared logic)  │   │
│ └────────┬─────────┘  └──────────────────────┘  └──────────────────┘   │
│  ┌───────┼──────┐                                                        │
│  ▼       ▼      ▼      ┌───────────────┐  ┌──────────────────────────┐  │
│ IciciCC SBI Generic    │ AsyncJobService│  │ PersistenceGateway       │  │
│ Parser Parser Parser   │ (Feature 8)   │  │ (toggle: NoOp / Postgres)│  │
│                        └───────────────┘  └──────────────────────────┘  │
│  ┌──────────────────────┐  ┌──────────────────────────────────────────┐  │
│  │ PdfReportGenerator   │  │  ExcelReportGenerator (Apache POI)       │  │
│  │ (OpenPDF, Feature 5) │  │  4 sheets: Txns / Mode / Merchant / Month│  │
│  └──────────────────────┘  └──────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

**Key responsibilities:**

| Component | Responsibility |
|---|---|
| `RateLimitFilter` | Per-IP token bucket; blocks request with 429 before controller |
| `AnalyzeController` | Validates file, checks dedup + cache, orchestrates full pipeline |
| `BankStatementParser` | PDFBox text extraction, sanitization, dispatches to correct parser |
| `BankParserRegistry` | Auto-detects bank format via `supports()` in `@Order` sequence |
| `BankParser.extractCustomerDetails()` | Each parser extracts name, account number, branch, IFSC, PAN, etc. from the PDF header |
| `TransactionAnalyzer` | Enriches transactions — payment mode, merchant, category |
| `InsightService` | Computes spending insights — highest spend, recurring, unusual |
| `DuplicateDetector` | Groups transactions by (desc + debit + credit) key; finds duplicates |
| `SummaryBuilder` | Shared summary-building logic used by both sync and async paths |
| `AsyncJobService` | Submits analysis jobs, tracks status in-memory, cleans up expired jobs |
| `PersistenceGateway` | Interface — routes to real DB or no-op depending on toggle |
| `WebhookService` | Async HTTP POST of results to caller-provided URL |
| `ExcelReportGenerator` | Writes 4-sheet `.xlsx` report using Apache POI |
| `PdfReportGenerator` | Writes multi-section `.pdf` report using OpenPDF |

---

## Low Level Design (LLD)

### Package Structure

```
com.bankanalyzer
├── BankAnalyzerApplication.java        Spring Boot entry (@EnableCaching, @EnableScheduling)
├── Main.java                           CLI entry point (no Spring context)
│
├── api/
│   ├── AnalyzeController.java          9 REST endpoints (single, multi, async, pdf-report)
│   ├── GlobalExceptionHandler.java     @ControllerAdvice — 4xx/5xx handling
│   └── dto/
│       ├── SummaryResponse.java        uploadId, detectedBank(s), insights, duplicates, customerDetails, ...
│       ├── CustomerDetails.java        19-field header extraction: name, account, branch, IFSC, PAN, ...
│       ├── SpendingInsights.java       highestSpend, averageMonthly, recurring, unusual
│       ├── DuplicateGroup.java         description, debit, credit, count, occurrenceDates (F3)
│       ├── SubmitJobResponse.java      jobId, statusUrl, message (Feature 8)
│       ├── JobStatusResponse.java      jobId, status, result, error, timestamps (Feature 8)
│       ├── RecurringTransactionDto.java
│       ├── UnusualTransactionDto.java
│       ├── PaymentModeSummary.java
│       ├── MerchantSummary.java
│       └── MonthSummary.java
│
├── parser/
│   ├── BankParser.java                 Strategy interface (bankName, statementType, supports, parse, extractCustomerDetails)
│   ├── AbstractBankParser.java         Shared utilities (date/amount parsing, SKIP_PATTERN)
│   ├── BankParserRegistry.java         Factory — resolves parser via supports()
│   ├── BankStatementParser.java        Orchestrator — PDF → text → ParseResult
│   └── impl/
│       ├── IciciCreditCardParser.java  @Order(1) — DD-MMM-YY format
│       ├── IciciSavingsParser.java     @Order(2) — DD/MM/YYYY, 3-column
│       ├── SbiParser.java              @Order(3) — dual-date, dash = zero
│       └── GenericBankParser.java      @Order(LOWEST) — fallback
│
├── analyzer/
│   └── TransactionAnalyzer.java        Payment mode + merchant + category (@Cacheable)
│
├── service/
│   ├── CategoryTagger.java             100+ keyword → Category map (@Cacheable)
│   ├── InsightService.java             Highest spend, recurring, unusual transaction detection
│   ├── DuplicateDetector.java          Groups by (desc+debit+credit) key; returns DuplicateGroup list (F3)
│   ├── SummaryBuilder.java             Shared buildSummary logic (used by sync + async paths)
│   ├── AsyncJobService.java            ConcurrentHashMap job store, @Async processing, @Scheduled cleanup (F8)
│   ├── PersistenceGateway.java         Interface — findDuplicate / save
│   ├── NoOpPersistenceGateway.java     Active when persistence.enabled=false (default)
│   ├── StatementPersistenceService.java Active when persistence.enabled=true
│   └── WebhookService.java             Async HTTP POST with 3-retry + SSRF guard
│
├── report/
│   ├── ExcelReportGenerator.java       4-sheet XLSX writer (Apache POI)
│   └── PdfReportGenerator.java         6-section PDF writer (OpenPDF) — Feature 5
│
├── model/
│   ├── Transaction.java                Domain model — date, debit, credit, paymentMode, category
│   ├── ParseResult.java                Parser output — transactions + bankName + statementType + customerDetails
│   ├── PaymentMode.java                UPI, NEFT, RTGS, IMPS, ATM, CARD_POS, CHEQUE, ECS_NACH, OTHER
│   ├── StatementType.java              SAVINGS_ACCOUNT, CURRENT_ACCOUNT, CREDIT_CARD
│   ├── JobStatus.java                  PENDING, PROCESSING, DONE, FAILED (Feature 8)
│   ├── Category.java                   FOOD_DINING, SHOPPING, FUEL, TRAVEL, HEALTH, ... (14 total)
│   └── entity/
│       ├── StatementUploadEntity.java  JPA: statement_uploads table
│       └── TransactionEntity.java      JPA: transactions table
│
├── repository/
│   ├── StatementUploadRepository.java
│   └── TransactionRepository.java
│
├── config/
│   ├── CacheConfig.java                Caffeine: paymentMode, merchant, category, analysis caches
│   ├── PersistenceConfig.java          Conditional JPA/Flyway/DataSource (@ConditionalOnProperty)
│   ├── AsyncConfig.java                Webhook + job thread pool + RestTemplate bean
│   ├── RateLimitProperties.java        @ConfigurationProperties(prefix="ratelimit")
│   └── DedupProperties.java            @ConfigurationProperties(prefix="dedup")
│
└── filter/
    └── RateLimitFilter.java            OncePerRequestFilter — Bucket4j token bucket per IP
```

### Class Relationships

```
BankParser (interface)
    │  bankName(), statementType(), supports(), parse()
    │  extractCustomerDetails()  ← default impl returns empty; overridden by each concrete parser
    └── AbstractBankParser (abstract — shared utilities: parseDate, parseAmount, SKIP_PATTERN)
            ├── IciciCreditCardParser  (overrides extractCustomerDetails)
            ├── IciciSavingsParser     (overrides extractCustomerDetails)
            ├── SbiParser              (overrides extractCustomerDetails — 19 fields)
            └── GenericBankParser (fallback — uses default empty CustomerDetails)

PersistenceGateway (interface)
    ├── NoOpPersistenceGateway      @ConditionalOnProperty(persistence.enabled=false)
    └── StatementPersistenceService @ConditionalOnProperty(persistence.enabled=true)

SummaryBuilder  ──▶ TransactionAnalyzer
                ──▶ InsightService
                ──▶ DuplicateDetector

AsyncJobService ──▶ BankStatementParser
                ──▶ TransactionAnalyzer
                ──▶ SummaryBuilder
                ──▶ PersistenceGateway

AnalyzeController
    ├── BankStatementParser ──▶ BankParserRegistry ──▶ List<BankParser>
    ├── TransactionAnalyzer ──▶ CategoryTagger
    ├── SummaryBuilder      ──▶ InsightService, DuplicateDetector
    ├── AsyncJobService
    ├── ExcelReportGenerator
    ├── PdfReportGenerator
    ├── PersistenceGateway  (injected — either NoOp or real)
    ├── WebhookService
    └── CacheManager (Caffeine)
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

| Parser | Statement Type | Date Format | Detection Keywords | Customer Fields Extracted |
|---|---|---|---|---|
| `IciciCreditCardParser` | Credit Card | `DD-MMM-YY` | `ICICI` + `Credit Card` | Name, account, statement period, closing balance |
| `IciciSavingsParser` | Savings Account | `DD/MM/YYYY` | `ICICI` (no CC keyword) | Name, account, branch, IFSC, statement period |
| `SbiParser` | Savings Account | `DD/MM/YYYY DD/MM/YYYY` | `State Bank of India`, `SBIN` | 19 fields — name, account, branch, IFSC, MICR, CIF, PAN, email, mobile, KYC status, segment, account status, open date, statement period, closing balance, currency, nominee |
| `GenericBankParser` | Savings Account | `DD/MM/YYYY`, `DD-MM-YYYY`, `DD MMM YYYY` | fallback — always matches | None (empty `CustomerDetails`) |

---

## Flow Diagram

```
POST /api/analyze/summary?webhookUrl=...
              │
              ▼
   ┌─────────────────────┐
   │   RateLimitFilter   │──── 429 Too Many Requests (if IP exhausted)
   └────────┬────────────┘
            │ token consumed
            ▼
   ┌─────────────────────┐
   │  AnalyzeController  │
   │  read file bytes    │
   │  compute MD5 hash   │
   └────────┬────────────┘
            │
            ▼
   ┌─────────────────────┐        ┌──────────────────────────────┐
   │  Dedup check        │──HIT──▶│  409 Conflict                │
   │  PersistenceGateway │        │  { uploadId, detectedBank }  │
   │  findDuplicate()    │        └──────────────────────────────┘
   └────────┬────────────┘
            │ no duplicate
            ▼
   ┌─────────────────────┐        ┌──────────────────────────────┐
   │   Cache lookup      │──HIT──▶│  Return cached result        │
   │   (hash:summary)    │        │  (skip all processing)       │
   └────────┬────────────┘        └──────────────────────────────┘
            │ MISS
            ▼
   ┌─────────────────────┐
   │ BankStatementParser │
   │  PDFBox extract     │
   │  sanitize()         │
   │  → ParseResult      │
   └────────┬────────────┘
            │ (bankName, statementType, raw transactions)
            ▼
   ┌─────────────────────┐
   │ BankParserRegistry  │
   │  supports() check   │──── first @Order match wins
   └────────┬────────────┘
            │ selected parser
            ▼
   ┌─────────────────────┐
   │  BankParser.parse() │
   │  regex line-by-line │
   │  multi-line desc    │
   └────────┬────────────┘
            │ List<Transaction>
            ▼
   ┌──────────────────────────┐
   │  extractCustomerDetails()│
   │  regex on PDF header     │
   │  → CustomerDetails       │
   └────────┬─────────────────┘
            │ name, account, branch, IFSC, PAN, ...
            ▼
   ┌──────────────────────────┐
   │   TransactionAnalyzer    │
   │  detectPaymentMode() ◀───── @Cacheable("paymentMode")
   │  extractMerchant()   ◀───── @Cacheable("merchant")
   │  categorize()        ◀───── @Cacheable("category")
   └────────┬─────────────────┘
            │ enriched transactions
            ▼
   ┌─────────────────────┐
   │   InsightService    │
   │  highestSpendDay    │
   │  recurringTxns      │
   │  unusualTxns (2σ)   │
   └────────┬────────────┘
            │ SpendingInsights
            ▼
   ┌─────────────────────┐
   │  PersistenceGateway │──── NoOp (disabled) or PostgreSQL (enabled)
   │  save(upload + txns)│
   └────────┬────────────┘
            │ uploadId (or null if disabled)
            ▼
   ┌─────────────────────┐
   │  cache.put(hash,    │
   │    result)          │
   └────────┬────────────┘
            │
     ┌──────┴──────────────┐
     ▼                     ▼
  JSON Summary          XLSX Report       ──── WebhookService.notify() [async]
  (with insights)       (POI, 4 sheets)
```

---

## Design Patterns

### Strategy + Registry (Parser)
`BankParser` is a strategy interface. `BankParserRegistry` holds all `BankParser` beans (Spring-injected in `@Order` sequence) and resolves the correct one by calling `supports()` on each.

**Benefit:** Adding a new bank = one new `@Component` class. Zero changes to existing code (Open/Closed Principle).

### Template Method (Abstract Parser)
`AbstractBankParser` provides shared utilities (`parseDate`, `parseAmount`, `buildDebitCreditTransaction`, `SKIP_PATTERN`). Concrete parsers only implement `supports()`, `parse()`, and `bankName()`.

### Strategy (Persistence Toggle)
`PersistenceGateway` is a strategy interface with two implementations conditionally registered via `@ConditionalOnProperty`. The controller injects the interface and has no knowledge of which is active.

```
persistence.enabled=false  →  NoOpPersistenceGateway    (default, no DB needed)
persistence.enabled=true   →  StatementPersistenceService (PostgreSQL)
```

### Filter Chain (Rate Limiting)
`RateLimitFilter` extends `OncePerRequestFilter` and runs before any controller logic.

### Cache-Aside (3 levels)
| Level | Where | Key |
|---|---|---|
| Method | `detectPaymentMode`, `extractMerchant`, `categorize` | description string |
| Request | `AnalyzeController` (programmatic) | MD5 of PDF bytes |

---

## Features

### Customer Details Extraction

Every `/api/analyze/summary` response includes a `customerDetails` object populated from the PDF header — no manual input required.

**How it works:**
1. `BankStatementParser` extracts the raw text via PDFBox, then calls `parser.extractCustomerDetails(rawText)`
2. Each concrete `BankParser` overrides `extractCustomerDetails()` and uses bank-specific regex patterns to pull fields from the statement header
3. The resulting `CustomerDetails` object is stored in `ParseResult` and flows into the JSON summary, XLSX report (first sheet), and PDF report (header grid)

**SBI — 19 extracted fields:**

| Field | Source pattern |
|---|---|
| `customerName` | Title-prefixed name before `State Bank of India` on same line |
| `accountNumber` | `Account Number : <digits>` |
| `product` | `Product : <text>` |
| `branch` | `Branch Name : <text>` |
| `branchCode` | `Branch Code : <digits>` |
| `ifscCode` | `IFSC Code : <alphanum>` |
| `micrCode` | `MICR Code : <digits>` |
| `cifNumber` | `CIF Number : <digits>` |
| `email` | `Email ID <email>` |
| `mobile` | `Mobile Number <digits>` |
| `pan` | `PAN <XXXXXNNNNX>` |
| `kycStatus` | `KYC Status <word>` |
| `segment` | `Segment <word>` |
| `accountStatus` | `Account Status : <word>` |
| `accountOpenDate` | `Account open Date : <DD/MM/YYYY>` |
| `statementPeriod` | `Statement From : <date> to <date>` |
| `statementDate` | `Date of Statement : <date>` |
| `closingBalance` | `Clear Balance : <amount CR/DR>` |
| `currency` | `Currency : <INR>` |
| `nomineeNam` | `Nominee Name : <text>` |

**JSON response example:**

```json
"customerDetails": {
  "customerName": "Mr. Tejas Chandra Gowda",
  "accountNumber": "12345678901",
  "product": "Savings Account",
  "branch": "Jayanagar Branch",
  "branchCode": "01234",
  "ifscCode": "SBIN0001234",
  "micrCode": "560002001",
  "cifNumber": "98765432",
  "email": "tejas@example.com",
  "mobile": "9876543210",
  "pan": "ABCDE1234F",
  "kycStatus": "KYC Done",
  "segment": "General",
  "accountStatus": "Regular",
  "accountOpenDate": "01/04/2010",
  "statementPeriod": "01/01/2024 to 31/03/2024",
  "statementDate": "04-04-2026",
  "closingBalance": "12,345.67CR",
  "currency": "INR",
  "nomineeNam": "Priya Gowda"
}
```

Fields that cannot be found are omitted from the JSON (`@JsonInclude(NON_NULL)`).

**In reports:**
- **XLSX** — "Customer Details" is the first sheet, rendered as label-value rows with a blue-tinted header style
- **PDF** — 4-column grid printed at the top of the report before the transactions table

---

### Multi-file Upload & Merge

Upload up to **10 PDF statements at once** — from the same or different banks. All transactions are merged, enriched, deduplicated-detected, and returned as a single unified summary.

```bash
# Merged JSON summary from 3 statements
curl -F "files=@sbi_jan.pdf" -F "files=@icici_jan.pdf" -F "files=@hdfc_jan.pdf" \
  http://localhost:8080/api/analyze/multi/summary

# Merged XLSX report
curl -F "files=@sbi_jan.pdf" -F "files=@icici_jan.pdf" \
  http://localhost:8080/api/analyze/multi/report --output merged.xlsx
```

**Response extras (over single-file):**
```json
{
  "detectedBank": "State Bank of India, ICICI Bank",
  "detectedBanks": ["State Bank of India", "ICICI Bank"],
  ...
}
```

- Transactions from all files are sorted chronologically after merge
- Each file is parsed independently using the correct bank parser (auto-detected)
- `byMonth`, `byMerchant`, `insights`, and `duplicates` cover the full merged dataset

---

### Duplicate Transaction Detection

Every `/api/analyze/summary` and `/api/analyze/multi/summary` response includes a `duplicates` field listing groups of transactions that appear more than once with the same description and amount.

**Detection key:** `normalized_description | debit | credit`

Transactions on different dates with the same key are flagged — this catches double-billing that spans days.

```json
"duplicates": [
  {
    "description": "UPI/Swiggy/Order payment",
    "debit": 349.00,
    "credit": 0.0,
    "count": 3,
    "occurrenceDates": ["2024-03-10", "2024-03-10", "2024-03-11"]
  }
]
```

- `duplicates` is `null` in the response when no duplicates are found (`@JsonInclude(NON_NULL)`)
- Also shown in the PDF report in a dedicated "Possible Duplicates" section

---

### Category Tagging

Every transaction is automatically tagged with a spending category based on merchant/description keywords.

**14 categories:** `FOOD_DINING`, `GROCERIES`, `SHOPPING`, `FUEL`, `UTILITIES`, `HEALTH`, `ENTERTAINMENT`, `TRAVEL`, `EDUCATION`, `EMI_LOAN`, `INVESTMENT`, `SALARY_INCOME`, `TRANSFER`, `OTHER`

**Sample keywords (100+ total):**

| Category | Keywords |
|---|---|
| FOOD_DINING | Swiggy, Zomato, Domino, McDonald, Cafe, Restaurant |
| TRAVEL | IRCTC, Ola, Uber, MakeMyTrip, IndiGo, Rapido |
| SHOPPING | Amazon, Flipkart, Myntra, Meesho, Nykaa |
| FUEL | HPCL, IOCL, BPCL, Reliance BP, Nayara |
| UTILITIES | Airtel, Jio, Electricity, Broadband, DTH Recharge |
| INVESTMENT | Zerodha, Groww, Mutual Fund, SIP, Kuvera |

Category is returned in every transaction and stored in DB when persistence is enabled.

---

### Spending Insights

Automatically computed and included in every `/api/analyze/summary` response under the `insights` field.

```json
"insights": {
  "highestSpendDay": "2024-03-15",
  "highestSpendDayAmount": 5430.00,
  "highestSpendMonth": "2024-03",
  "highestSpendMonthAmount": 45230.00,
  "averageMonthlySpend": 32000.00,
  "recurringTransactions": [
    { "merchantName": "Swiggy", "occurrences": 8, "averageAmount": 320.00, "totalAmount": 2560.00 }
  ],
  "unusualTransactions": [
    { "date": "2024-03-15", "description": "...", "merchantName": "Amazon", "amount": 15000.00 }
  ]
}
```

**How each insight is computed:**
- **Highest spend day/month** — group debits by date/month, find max
- **Average monthly spend** — total debits ÷ number of distinct months
- **Recurring transactions** — merchants with ≥ 2 debit occurrences, sorted by frequency
- **Unusual transactions** — debits above `mean + 2 × standard deviation`, top 10 shown

---

### PDF Report Output

Download a formatted PDF report directly — no Excel required.

```bash
curl -F "file=@statement.pdf" \
  http://localhost:8080/api/analyze/pdf-report --output report.pdf
```

**PDF sections:**

| Section | Content |
|---|---|
| Summary | Bank name, period, total debit/credit, transaction count |
| All Transactions | Full table — date, description, mode, merchant, debit, credit |
| Spend by Category | Debit totals per category (FOOD_DINING, TRAVEL, etc.) |
| Spend by Payment Mode | Count + debit + credit per mode |
| Monthly Breakdown | Month-by-month debit/credit counts and totals |
| Duplicate Transactions | Only shown when duplicates are detected |

- Built with **OpenPDF** (open-source fork of iText 2) — no licensing restrictions
- Landscape A4, alternating row shading, header/footer with page numbers
- Response is `Content-Type: application/pdf` with `Content-Disposition: attachment`

---

### Async Processing & Job Polling

For large statements or automated pipelines, submit a file asynchronously and poll for the result.

**Step 1 — Submit:**
```bash
curl -F "file=@statement.pdf" http://localhost:8080/api/analyze/submit
```
```json
{
  "jobId": "a3f4c7b2-...",
  "statusUrl": "http://localhost:8080/api/analyze/status/a3f4c7b2-...",
  "message": "Job accepted. Poll statusUrl for results."
}
```
Returns `HTTP 202 Accepted` immediately.

**Step 2 — Poll:**
```bash
curl http://localhost:8080/api/analyze/status/a3f4c7b2-...
```

| Status | HTTP | Meaning |
|---|---|---|
| `PENDING` | 202 | Queued, not started yet |
| `PROCESSING` | 202 | Parse + analyze in progress |
| `DONE` | 200 | Full `SummaryResponse` in `result` field |
| `FAILED` | 500 | Error message in `error` field |

**Done response example:**
```json
{
  "jobId": "a3f4c7b2-...",
  "status": "DONE",
  "submittedAt": "2024-04-01T10:00:00Z",
  "completedAt": "2024-04-01T10:00:03Z",
  "result": { ... full SummaryResponse ... }
}
```

**Implementation details:**
- Jobs stored in `ConcurrentHashMap` (in-memory; swap for Redis in multi-node deployments)
- Processing runs on the shared `webhookExecutor` thread pool (`@Async`)
- Jobs are purged automatically after **1 hour** via `@Scheduled` cleanup (runs every 5 minutes)
- Polling for an expired/unknown jobId returns `FAILED` with a descriptive error

---

### Persistence (PostgreSQL)

Disabled by default. Enable by setting `persistence.enabled=true` and providing DB credentials.

#### Toggle

```properties
# Default — no DB connection attempted, app runs fully in-memory
persistence.enabled=false

# Enable — connects to PostgreSQL, runs Flyway migrations on startup
persistence.enabled=true
spring.datasource.url=jdbc:postgresql://localhost:5432/bankanalyzer
spring.datasource.username=postgres
spring.datasource.password=postgres
```

#### How the toggle works

| State | Active Bean | DB Connection | Dedup |
|---|---|---|---|
| `persistence.enabled=false` | `NoOpPersistenceGateway` | None | Disabled |
| `persistence.enabled=true` | `StatementPersistenceService` | PostgreSQL | Active |

When disabled, `BankAnalyzerApplication` excludes `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, and `FlywayAutoConfiguration` — no datasource bean is ever created.

#### Database Schema

```sql
statement_uploads          -- one row per PDF upload
  id, file_hash, original_filename, bank_name,
  statement_type, transaction_count, uploaded_at

transactions               -- one row per transaction
  id, upload_id (FK), txn_date, description,
  debit, credit, balance, payment_mode, merchant_name, category
```

Flyway runs `V1__init.sql` automatically on startup when persistence is enabled.

#### Start PostgreSQL (Docker)

```bash
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=bankanalyzer \
  -e POSTGRES_PASSWORD=postgres \
  postgres:16
```

---

### Webhook / Callback

Pass an optional `webhookUrl` query parameter to receive results asynchronously after processing.

```bash
curl -F "file=@statement.pdf" \
  "http://localhost:8080/api/analyze/summary?webhookUrl=https://your-server.com/callback"
```

- Runs on a dedicated `webhook-` thread pool (`@Async`) — never blocks the HTTP response
- POSTs the full `SummaryResponse` JSON to the callback URL
- Retries up to **3 times** with exponential backoff (1s, 2s, 3s)
- **SSRF guard** — rejects `localhost`, `127.x`, `10.x`, `192.168.x`, `172.16–31.x`

---

### File Deduplication

When persistence is enabled, the same PDF cannot be submitted twice within the configured window.

```properties
dedup.enabled=true
dedup.window-hours=24
```

If a duplicate is detected, the API returns `HTTP 409 Conflict`:

```json
{
  "uploadId": 42,
  "detectedBank": "State Bank of India",
  "totalTransactions": 137
}
```

Deduplication is keyed on **MD5 hash of the raw PDF bytes** — same file, same hash, rejected.

> Deduplication is automatically disabled when `persistence.enabled=false` since there is no DB to check against.

---

## API Reference

### `GET /api/health`
```json
{ "status": "UP", "service": "bank-statement-analyzer" }
```

### `POST /api/analyze/summary`

Upload a PDF, get a full JSON breakdown including insights.

```bash
# Basic
curl -F "file=@statement.pdf" http://localhost:8080/api/analyze/summary

# With webhook callback
curl -F "file=@statement.pdf" \
  "http://localhost:8080/api/analyze/summary?webhookUrl=https://example.com/cb"
```

**Response:**
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
    { "month": "2024-04", "debitCount": 12, "totalDebit": 28000.00, "creditCount": 2, "totalCredit": 5000.00 }
  ],
  "insights": {
    "highestSpendDay": "2024-04-15",
    "highestSpendDayAmount": 4200.00,
    "highestSpendMonth": "2024-04",
    "highestSpendMonthAmount": 28000.00,
    "averageMonthlySpend": 24500.00,
    "recurringTransactions": [...],
    "unusualTransactions": [...]
  },
  "customerDetails": {
    "customerName": "Mr. Tejas Chandra Gowda",
    "accountNumber": "12345678901",
    "branch": "Jayanagar Branch",
    "ifscCode": "SBIN0001234",
    "pan": "ABCDE1234F",
    "statementPeriod": "01/01/2024 to 31/03/2024",
    "closingBalance": "12,345.67CR"
  }
}
```

### `POST /api/analyze/report`

Upload a PDF, download a 4-sheet `.xlsx` report.

```bash
curl -F "file=@statement.pdf" http://localhost:8080/api/analyze/report --output report.xlsx
```

**Excel sheets:**
1. **Customer Details** — extracted header fields (name, account, branch, IFSC, PAN, etc.) — first sheet when available
2. **All Transactions** — date, description, debit, credit, balance, payment mode, merchant, category
3. **By Payment Mode** — totals per UPI / NEFT / ATM etc.
4. **By Merchant** — top 20 merchants by spend
5. **By Month** — monthly debit/credit totals

### `POST /api/analyze/pdf-report`

Upload a PDF, download a multi-section PDF report.

```bash
curl -F "file=@statement.pdf" http://localhost:8080/api/analyze/pdf-report --output report.pdf
```

### `POST /api/analyze/multi/summary`

Upload multiple PDFs, get a merged JSON summary.

```bash
curl -F "files=@jan.pdf" -F "files=@feb.pdf" http://localhost:8080/api/analyze/multi/summary
```

### `POST /api/analyze/multi/report`

Upload multiple PDFs, download a merged XLSX report.

```bash
curl -F "files=@jan.pdf" -F "files=@feb.pdf" \
  http://localhost:8080/api/analyze/multi/report --output merged.xlsx
```

### `POST /api/analyze/submit`

Submit a file for async background processing. Returns `HTTP 202` with a `jobId`.

```bash
curl -F "file=@statement.pdf" http://localhost:8080/api/analyze/submit
```

### `GET /api/analyze/status/{jobId}`

Poll for job result.

```bash
curl http://localhost:8080/api/analyze/status/{jobId}
```

### `POST /api/analyze/raw-text`

Returns raw text extracted by PDFBox — useful for debugging unknown statement formats.

```bash
curl -F "file=@statement.pdf" http://localhost:8080/api/analyze/raw-text
```

---

## Rate Limiting

Applied only to `/api/analyze/*` endpoints. Health check is never rate-limited.

```properties
ratelimit.enabled=true
ratelimit.capacity=20            # max burst size
ratelimit.refill-tokens=20       # tokens restored per period
ratelimit.refill-duration=1
ratelimit.refill-unit=MINUTES    # SECONDS / MINUTES / HOURS
ratelimit.cache-size=10000       # max unique IPs tracked simultaneously
ratelimit.cache-expire-minutes=10
```

On limit breach — `HTTP 429` with:
- `Retry-After` header (seconds until next token)
- `X-Rate-Limit-Capacity` and `X-Rate-Limit-Remaining` headers on every response
- JSON body with human-readable message

---

## Caching

| Cache | Key | TTL | Purpose |
|---|---|---|---|
| `paymentMode` | description | none | `detectPaymentMode()` — pure function |
| `merchant` | description + mode | none | `extractMerchant()` — pure function |
| `category` | description | none | `categorize()` — pure function |
| `analysis` | MD5 of PDF bytes | 1 hour | Full parse + analyze result per file |

All caches use **Caffeine** (in-memory). To switch to Redis, replace `CacheConfig` with a `RedisCacheManager` and add `spring-boot-starter-data-redis`.

---

## Configuration Reference

```properties
# ── Server ─────────────────────────────────────────────────────────────────
server.port=8080
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=52MB

# ── Persistence toggle ──────────────────────────────────────────────────────
persistence.enabled=false           # true = PostgreSQL; false = in-memory only (default)

# ── PostgreSQL (only used when persistence.enabled=true) ────────────────────
#spring.datasource.url=jdbc:postgresql://localhost:5432/bankanalyzer
#spring.datasource.username=postgres
#spring.datasource.password=postgres

# ── Deduplication (only active when persistence.enabled=true) ───────────────
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

# ── Logging ─────────────────────────────────────────────────────────────────
logging.level.com.bankanalyzer=INFO
```

---

## Running the App

**Prerequisites:** Java 17+, Maven 3.9+

```bash
# Build
mvn clean package -DskipTests

# Run (persistence disabled by default — no DB needed)
java -jar target/bank-statement-analyzer-1.0.0.jar

# Run with persistence enabled
java -jar target/bank-statement-analyzer-1.0.0.jar \
  --persistence.enabled=true \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/bankanalyzer \
  --spring.datasource.username=postgres \
  --spring.datasource.password=postgres

# Health check
curl http://localhost:8080/api/health
```

---

## Docker

```bash
# Build image
docker build -t bank-statement-analyzer:latest .

# Run (persistence disabled)
docker run -p 8080:8080 bank-statement-analyzer:latest

# Run with persistence enabled
docker run -p 8080:8080 \
  -e PERSISTENCE_ENABLED=true \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/bankanalyzer \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  bank-statement-analyzer:latest
```

**Dockerfile** uses a two-stage build:
- **Stage 1** (`maven:3.9-eclipse-temurin-21-alpine`) — compiles and packages fat jar; Maven `.m2` cache mounted via BuildKit
- **Stage 2** (`eclipse-temurin:21-jre-alpine`) — copies only the jar; final image ~180 MB

---

## Adding a New Bank

1. Create `src/main/java/com/bankanalyzer/parser/impl/XyzBankParser.java`
2. Extend `AbstractBankParser`, implement `BankParser`
3. Annotate `@Component` + `@Order(N)` (lower number = checked before Generic fallback)

```java
@Slf4j
@Component
@Order(4)
public class XyzBankParser extends AbstractBankParser {

    private static final Pattern TX_PATTERN = Pattern.compile("...");

    @Override public String bankName()             { return "XYZ Bank"; }
    @Override public StatementType statementType() { return StatementType.SAVINGS_ACCOUNT; }

    @Override
    public boolean supports(String rawText) {
        return rawText.contains("XYZ Bank");
    }

    @Override
    public List<Transaction> parse(String text) {
        // line-by-line regex parsing
        // use buildDebitCreditTransaction() or buildAmountTransaction() from AbstractBankParser
    }

    @Override
    public CustomerDetails extractCustomerDetails(String rawText) {
        // optional — pull name, account number, branch, etc. from the PDF header
        // return CustomerDetails.builder().build() to leave all fields null
        return CustomerDetails.builder()
            .accountNumber(/* regex match */)
            .build();
    }
}
```

No other files need to change. Spring auto-registers and orders the parser.

> **Tip:** Use `POST /api/analyze/raw-text` to inspect what PDFBox extracts before writing the regex pattern, then craft `extractCustomerDetails()` regex patterns against the same raw text.

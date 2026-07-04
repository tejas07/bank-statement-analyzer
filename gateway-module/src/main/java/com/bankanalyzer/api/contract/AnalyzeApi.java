package com.bankanalyzer.api.contract;

import com.bankanalyzer.api.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI / Swagger contract for {@code AnalyzeController}.
 * All documentation annotations live here; the controller stays clean.
 */
@Tag(name = "Analysis",
        description = "Parse bank statement PDFs into structured JSON summaries, XLSX/PDF reports, and async jobs")
public interface AnalyzeApi {

    // ── Health ────────────────────────────────────────────────────────────────

    @Tag(name = "Health")
    @Operation(summary = "Health check", description = "Returns UP when the service is running.")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    ResponseEntity<Map<String, String>> health();

    // ── Single-file summary ───────────────────────────────────────────────────

    @Operation(
            summary = "Analyse a single PDF statement",
            description = """
                    Parses the uploaded bank statement PDF and returns a full JSON summary:
                    detected bank, statement type, total debit/credit, breakdown by payment mode,
                    top 20 merchants, monthly totals, spending insights (highest-spend day/month,
                    recurring transactions, unusual transactions), possible duplicate transactions,
                    and extracted customer details.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis successful"),
            @ApiResponse(responseCode = "409", description = "Duplicate file — same PDF was uploaded before",
                    content = @Content(schema = @Schema(implementation = SummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "No file provided or non-PDF file",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "422", description = "PDF could not be parsed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SummaryResponse> getSummary(
            @Parameter(description = "Bank statement PDF (max 50 MB)") MultipartFile file,
            @Parameter(description = "Optional callback URL — full SummaryResponse will be POSTed here asynchronously")
            String webhookUrl
    ) throws IOException;

    // ── Single-file XLSX report ───────────────────────────────────────────────

    @Operation(
            summary = "Download XLSX report for a single statement",
            description = """
                    Parses the PDF and returns a 5-sheet Excel workbook:
                    Customer Details, All Transactions, By Payment Mode, By Merchant (top 20), By Month.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "XLSX file (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)"),
            @ApiResponse(responseCode = "409", description = "Duplicate file"),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    void downloadReport(
            @Parameter(description = "Bank statement PDF (max 50 MB)") MultipartFile file,
            HttpServletResponse response
    ) throws IOException;

    // ── Single-file PDF report ────────────────────────────────────────────────

    @Operation(
            summary = "Download PDF report for a single statement",
            description = "Returns a formatted landscape-A4 PDF report with summary, " +
                    "transactions table, category/mode/monthly breakdowns, and a duplicates section.")
    @ApiResponse(responseCode = "200", description = "PDF file (application/pdf)")
    void downloadPdfReport(
            @Parameter(description = "Bank statement PDF (max 50 MB)") MultipartFile file,
            HttpServletResponse response
    ) throws IOException;

    // ── Multi-file summary ────────────────────────────────────────────────────

    @Operation(
            summary = "Analyse multiple PDFs — merged summary",
            description = "Upload up to 10 PDF statements (same or different banks). " +
                    "Transactions are merged, sorted chronologically, and returned as a single unified summary.")
    @ApiResponse(responseCode = "200", description = "Merged analysis successful")
    ResponseEntity<SummaryResponse> getMultiSummary(
            @Parameter(description = "Up to 10 bank statement PDFs") List<MultipartFile> files,
            @Parameter(description = "Optional webhook callback URL") String webhookUrl
    ) throws IOException;

    // ── Multi-file XLSX report ────────────────────────────────────────────────

    @Operation(
            summary = "Download merged XLSX for multiple PDFs",
            description = "Upload up to 10 PDFs and receive a single merged Excel report.")
    @ApiResponse(responseCode = "200", description = "Merged XLSX file")
    void downloadMultiReport(
            @Parameter(description = "Up to 10 bank statement PDFs") List<MultipartFile> files,
            HttpServletResponse response
    ) throws IOException;

    // ── Raw text (debug) ──────────────────────────────────────────────────────

    @Operation(
            summary = "Extract raw text from PDF (debug)",
            description = "Returns the raw text extracted by PDFBox — useful for diagnosing why " +
                    "an unknown statement format is not parsed correctly.")
    @ApiResponse(responseCode = "200", description = "Plain text content of the PDF")
    ResponseEntity<String> getRawText(
            @Parameter(description = "Bank statement PDF (max 50 MB)") MultipartFile file
    ) throws IOException;

    // ── Async submit ──────────────────────────────────────────────────────────

    @Operation(
            summary = "Submit PDF for async background analysis",
            description = "Accepts the file immediately (HTTP 202) and processes it in the background. " +
                    "Poll the returned `statusUrl` or connect to the SSE stream endpoint for real-time updates.")
    @ApiResponse(responseCode = "202", description = "Job accepted — use statusUrl or streamUrl to track progress")
    ResponseEntity<SubmitJobResponse> submitJob(
            @Parameter(description = "Bank statement PDF (max 50 MB)") MultipartFile file,
            HttpServletRequest request
    ) throws IOException;

    // ── Async status poll ─────────────────────────────────────────────────────

    @Operation(
            summary = "Poll async job status",
            description = "Returns PENDING (202), PROCESSING (202), DONE (200), or FAILED (500). " +
                    "When DONE the full SummaryResponse is in the `result` field. " +
                    "For push-based updates use the `/stream/{jobId}` SSE endpoint instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job DONE — result field populated"),
            @ApiResponse(responseCode = "202", description = "Job still PENDING or PROCESSING"),
            @ApiResponse(responseCode = "500", description = "Job FAILED — error field populated")
    })
    ResponseEntity<JobStatusResponse> getJobStatus(
            @Parameter(description = "Job ID returned by /api/analyze/submit") String jobId
    );

    // ── SSE job stream ────────────────────────────────────────────────────────

    @Operation(
            summary = "Stream job status via Server-Sent Events",
            description = """
                    Opens a persistent SSE connection and pushes status updates every second
                    until the job reaches a terminal state (DONE or FAILED), then closes the stream.

                    Each event has:
                    - `event` field: `pending` | `processing` | `done` | `failed`
                    - `data` field: full `JobStatusResponse` JSON

                    Automatically times out after 5 minutes if the job has not completed.
                    Use this instead of polling `/status/{jobId}` for a real-time UX.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "SSE stream of JobStatusResponse events (text/event-stream)"),
            @ApiResponse(responseCode = "200",
                    description = "Stream completes when job is DONE or FAILED")
    })
    Flux<ServerSentEvent<JobStatusResponse>> streamJobStatus(
            @Parameter(description = "Job ID returned by /api/analyze/submit") String jobId
    );
}

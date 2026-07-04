package com.bankanalyzer.pipeline;

import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;

import java.util.List;

/**
 * Result of parsing, enriching, and persisting a statement — shared between the
 * summary path (which wraps this into a {@code SummaryResponse}) and the report
 * paths (which only need the enriched transactions and customer details).
 */
public record ParsedStatement(ParseResult parsed, List<Transaction> enriched, Long uploadId) {
}

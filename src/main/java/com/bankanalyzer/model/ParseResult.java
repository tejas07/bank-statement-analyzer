package com.bankanalyzer.model;

import com.bankanalyzer.api.dto.CustomerDetails;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Wraps parser output with bank metadata and customer details detected from the PDF text.
 */
@Getter
@RequiredArgsConstructor
public class ParseResult {
    private final List<Transaction> transactions;
    private final String bankName;
    private final StatementType statementType;
    private final CustomerDetails customerDetails;
}

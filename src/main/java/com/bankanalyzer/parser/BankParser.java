package com.bankanalyzer.parser;

import com.bankanalyzer.api.dto.CustomerDetails;
import com.bankanalyzer.model.StatementType;
import com.bankanalyzer.model.Transaction;

import java.util.List;

/**
 * Strategy interface for bank-specific statement parsers.
 * Each implementation handles one bank's PDF text format.
 */
public interface BankParser {

    /** Human-readable bank/product name for logging. */
    String bankName();

    /** Whether this is a credit card, savings, or current account statement. */
    StatementType statementType();

    /**
     * Returns true if this parser recognises the given raw PDF text.
     * Called by BankParserRegistry to auto-detect the correct parser.
     */
    boolean supports(String rawText);

    /**
     * Parses sanitized PDF text into a list of transactions.
     *
     * @param rawText sanitized text extracted from the PDF
     */
    List<Transaction> parse(String rawText);

    /**
     * Extracts customer/account metadata from the PDF header.
     * Default implementation returns an empty object; parsers override for their format.
     */
    default CustomerDetails extractCustomerDetails(String rawText) {
        return CustomerDetails.builder().build();
    }
}

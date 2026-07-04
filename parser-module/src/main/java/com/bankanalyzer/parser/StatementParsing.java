package com.bankanalyzer.parser;

import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Abstraction over PDF statement parsing — lets callers depend on this contract
 * rather than the concrete {@link BankStatementParser}, which becomes a mechanical
 * swap once parsing is extracted into its own service (see the microservices
 * extraction plan).
 */
public interface StatementParsing {

    String extractRawText(InputStream pdfStream) throws IOException;

    List<Transaction> parse(InputStream pdfStream) throws IOException;

    ParseResult parseWithMeta(InputStream pdfStream) throws IOException;

    List<Transaction> parse(File pdfFile) throws IOException;

    ParseResult parseWithMeta(File pdfFile) throws IOException;
}

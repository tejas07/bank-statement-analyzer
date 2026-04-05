package com.bankanalyzer.parser;

import com.bankanalyzer.model.Transaction;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared utilities for all bank parsers: date parsing, amount parsing,
 * skip patterns, and transaction builder helpers.
 */
@Slf4j
public abstract class AbstractBankParser implements BankParser {

    protected static final Pattern SKIP_PATTERN = Pattern.compile(
        "(?i)(statement of account|date.*narration|transaction.*date|" +
        "opening balance|closing balance|page \\d|brought forward|" +
        "carried forward|\\*{3,}|={3,}|-{5,}|account number|branch|ifsc|" +
        "customer id|available balance|statement period|" +
        "transaction details|card number|ref\\..*number|currency.*amount|" +
        "registered office|category of service|authenticated intimation|" +
        "^balance$|txn date|value date|description|chq.*no|ref.*no)"
    );

    protected static final Pattern AMOUNT_PATTERN = Pattern.compile("[\\d,]+\\.\\d{2}");

    // Case-insensitive: handles both "APR" and "Apr"
    private static final DateTimeFormatter CC_DATE_FMT = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yy")
        .toFormatter(Locale.ENGLISH);

    protected static final List<DateTimeFormatter> DATE_FORMATS = Arrays.asList(
        CC_DATE_FMT,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MMM/yyyy"),
        DateTimeFormatter.ofPattern("d MMM yyyy"),
        DateTimeFormatter.ofPattern("d/MM/yyyy"),
        DateTimeFormatter.ofPattern("d-MM-yyyy")
    );

    // ── Shared builder helpers ───────────────────────────────────────────────

    protected Transaction buildDebitCreditTransaction(Transaction.TransactionBuilder builder,
                                                       String desc, LocalDate date,
                                                       double debit, double credit, double balance) {
        if (date == null) return null;
        return builder
            .description(desc != null ? desc.trim() : "")
            .debit(debit)
            .credit(credit)
            .balance(balance)
            .build();
    }

    protected Transaction buildAmountTransaction(Transaction.TransactionBuilder builder,
                                                  String desc, List<Double> amounts, LocalDate date) {
        if (date == null || amounts == null || amounts.isEmpty()) return null;
        builder.description(desc != null ? desc.trim() : "");
        assignAmounts(builder, amounts);
        return builder.build();
    }

    /**
     * Strategy: last amount = balance; preceding = debit / credit.
     */
    private void assignAmounts(Transaction.TransactionBuilder builder, List<Double> amounts) {
        if (amounts.size() == 1) {
            builder.balance(amounts.get(0));
        } else if (amounts.size() == 2) {
            builder.debit(amounts.get(0));
            builder.balance(amounts.get(1));
        } else {
            builder.debit(amounts.get(0));
            builder.credit(amounts.get(1));
            builder.balance(amounts.get(amounts.size() - 1));
        }
    }

    // ── Shared parsing utilities ─────────────────────────────────────────────

    protected boolean isAmountOnlyLine(String line) {
        return line.matches("[\\d,\\s\\.]+") && AMOUNT_PATTERN.matcher(line).find();
    }

    protected static double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    protected static LocalDate parseDate(String raw) {
        String normalized = raw.trim().replaceAll("\\s+", " ");
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(normalized, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        log.debug("Could not parse date: '{}'", raw);
        return null;
    }
}

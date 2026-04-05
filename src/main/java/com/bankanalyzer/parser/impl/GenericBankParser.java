package com.bankanalyzer.parser.impl;

import com.bankanalyzer.model.StatementType;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.parser.AbstractBankParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback parser for any bank statement not matched by a specific parser.
 * Handles the common Indian bank format:
 *   DD/MM/YYYY  Description  [Debit]  [Credit]  Balance
 *
 * Always returns true from supports() — must be last in the registry order.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GenericBankParser extends AbstractBankParser {

    private static final Pattern TX_PATTERN = Pattern.compile(
        "^(\\d{2}[/\\-]\\d{2}[/\\-]\\d{4}|\\d{2}\\s+[A-Za-z]{3}\\s+\\d{4}|\\d{2}[/\\-][A-Za-z]{3}[/\\-]\\d{4})" +
        "\\s+(.*?)" +
        "\\s+([\\d,]+\\.\\d{2})" +
        "(?:\\s+([\\d,]+\\.\\d{2}))?" +
        "(?:\\s+([\\d,]+\\.\\d{2}))?" +
        "\\s*$"
    );

    @Override public String bankName()          { return "Generic"; }
    @Override public StatementType statementType() { return StatementType.SAVINGS_ACCOUNT; }
    @Override public boolean supports(String rawText) { return true; }  // always fallback

    @Override
    public List<Transaction> parse(String text) {
        List<Transaction> results = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        Transaction.TransactionBuilder pending = null;
        String pendingDesc = null;
        List<Double> pendingAmounts = null;
        LocalDate pendingDate = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || SKIP_PATTERN.matcher(line).find()) continue;

            Matcher m = TX_PATTERN.matcher(line);
            if (m.matches()) {
                if (pending != null) {
                    Transaction t = buildAmountTransaction(pending, pendingDesc, pendingAmounts, pendingDate);
                    if (t != null) results.add(t);
                }

                pendingDate = parseDate(m.group(1).trim());
                if (pendingDate == null) { pending = null; continue; }

                pendingDesc    = m.group(2).trim();
                pendingAmounts = extractAmounts(m);
                double bal     = pendingAmounts.isEmpty() ? 0 : pendingAmounts.get(pendingAmounts.size() - 1);
                pending = Transaction.builder().date(pendingDate).balance(bal);

            } else if (pending != null && !isAmountOnlyLine(line) && line.length() > 3) {
                pendingDesc = pendingDesc + " " + line;
            }
        }

        if (pending != null) {
            Transaction t = buildAmountTransaction(pending, pendingDesc, pendingAmounts, pendingDate);
            if (t != null) results.add(t);
        }

        log.info("[{}] Parsed {} transactions", bankName(), results.size());
        return results;
    }

    private List<Double> extractAmounts(Matcher m) {
        List<Double> amounts = new ArrayList<>();
        for (int g = 3; g <= 5; g++) {
            if (m.group(g) != null) amounts.add(parseAmount(m.group(g)));
        }
        return amounts;
    }
}

package com.bankanalyzer.parser.impl;

import com.bankanalyzer.api.dto.CustomerDetails;
import com.bankanalyzer.model.StatementType;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.parser.AbstractBankParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ICICI Bank savings/current account statements.
 *
 * Format: DD/MM/YYYY  Description  Debit  Credit  Balance
 */
@Slf4j
@Component
@Order(2)
public class IciciSavingsParser extends AbstractBankParser {

    private static final Pattern TX_PATTERN = Pattern.compile(
        "^(\\d{2}/\\d{2}/\\d{4})" +
        "\\s+(.*?)" +
        "\\s+([\\d,]+\\.\\d{2})" +          // debit
        "\\s+([\\d,]+\\.\\d{2})" +          // credit
        "\\s+([\\d,]+\\.\\d{2})" +          // balance
        "\\s*$"
    );

    @Override public String bankName()          { return "ICICI Savings Account"; }
    @Override public StatementType statementType() { return StatementType.SAVINGS_ACCOUNT; }

    @Override
    public boolean supports(String rawText) {
        return rawText.contains("ICICI") &&
               !rawText.contains("Credit Card") &&
               !rawText.contains("CREDIT CARD");
    }

    @Override
    public List<Transaction> parse(String text) {
        List<Transaction> results = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        Transaction.TransactionBuilder pending = null;
        String pendingDesc = null;
        LocalDate pendingDate = null;
        double pendingDebit = 0, pendingCredit = 0, pendingBalance = 0;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || SKIP_PATTERN.matcher(line).find()) continue;

            Matcher m = TX_PATTERN.matcher(line);
            if (m.matches()) {
                if (pending != null) {
                    Transaction t = buildDebitCreditTransaction(pending, pendingDesc, pendingDate, pendingDebit, pendingCredit, pendingBalance);
                    if (t != null) results.add(t);
                }

                pendingDate    = parseDate(m.group(1).trim());
                if (pendingDate == null) { pending = null; continue; }

                pendingDesc    = m.group(2).trim();
                pendingDebit   = parseAmount(m.group(3));
                pendingCredit  = parseAmount(m.group(4));
                pendingBalance = parseAmount(m.group(5));
                pending = Transaction.builder().date(pendingDate);

            } else if (pending != null && !isAmountOnlyLine(line) && line.length() > 3) {
                pendingDesc = pendingDesc + " " + line;
            }
        }

        if (pending != null) {
            Transaction t = buildDebitCreditTransaction(pending, pendingDesc, pendingDate, pendingDebit, pendingCredit, pendingBalance);
            if (t != null) results.add(t);
        }

        log.info("[{}] Parsed {} transactions", bankName(), results.size());
        return results;
    }

    @Override
    public CustomerDetails extractCustomerDetails(String rawText) {
        return CustomerDetails.builder()
            .customerName(extract(rawText, "(?i)customer\\s*name[:\\s]+([A-Za-z ]{3,50})", 1))
            .accountNumber(extract(rawText, "(?i)account\\s*no[.:]?\\s*([\\dX*]+)", 1))
            .product(extract(rawText, "(?i)account\\s*type[:\\s]+(.+)", 1))
            .branch(extract(rawText, "(?i)branch[:\\s]+([A-Za-z ]{3,40})", 1))
            .ifscCode(extract(rawText, "(?i)IFSC[:\\s]+([A-Z0-9]+)", 1))
            .email(extract(rawText, "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}", 0))
            .statementPeriod(extract(rawText, "(?i)statement\\s*(period|from)[:\\s]+(.+to.+)", 2))
            .closingBalance(extract(rawText, "(?i)closing\\s*balance[:\\s]+([\\d,\\.]+)", 1))
            .currency(extract(rawText, "(?i)currency[:\\s]+([A-Z]{3})", 1))
            .build();
    }

    private String extract(String text, String regex, int group) {
        java.util.regex.Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) return null;
        String val = (group == 0 ? m.group(0) : m.group(group)).trim();
        return val.isEmpty() ? null : val;
    }
}

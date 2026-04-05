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
 * Parses ICICI Bank credit card statements.
 *
 * Format: DD-MMM-YY [Ref#] Description  0.00  Amount
 *   positive amount = debit (purchase), negative = credit (payment/reversal)
 */
@Slf4j
@Component
@Order(1)
public class IciciCreditCardParser extends AbstractBankParser {

    private static final Pattern TX_PATTERN = Pattern.compile(
        "^(\\d{2}-[A-Za-z]{3}-\\d{2})" +
        "\\s+(.*?)" +
        "\\s+([\\d,]+\\.\\d{2})" +       // intl currency column (0.00 for domestic)
        "\\s+(-?[\\d,]+\\.\\d{2})" +      // actual amount: positive=debit, negative=credit
        "\\s*$"
    );

    // Strip leading ref numbers like "74332744097409596656227 " or "SR986556300 "
    private static final Pattern REF_PREFIX = Pattern.compile("^[A-Z0-9]{6,}\\s+");

    @Override public String bankName()          { return "ICICI Credit Card"; }
    @Override public StatementType statementType() { return StatementType.CREDIT_CARD; }

    @Override
    public boolean supports(String rawText) {
        return rawText.contains("ICICI") &&
               (rawText.contains("Credit Card") || rawText.contains("CREDIT CARD"));
    }

    @Override
    public List<Transaction> parse(String text) {
        List<Transaction> results = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        Transaction.TransactionBuilder pending = null;
        String pendingDesc = null;
        LocalDate pendingDate = null;
        double pendingDebit = 0, pendingCredit = 0;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || SKIP_PATTERN.matcher(line).find()) continue;

            Matcher m = TX_PATTERN.matcher(line);
            if (m.matches()) {
                if (pending != null) {
                    Transaction t = buildDebitCreditTransaction(pending, pendingDesc, pendingDate, pendingDebit, pendingCredit, 0);
                    if (t != null) results.add(t);
                }

                pendingDate = parseDate(m.group(1).trim());
                if (pendingDate == null) { pending = null; continue; }

                pendingDesc = REF_PREFIX.matcher(m.group(2).trim()).replaceFirst("").trim();
                double amount = parseAmount(m.group(4));
                pendingDebit  = amount > 0 ?  amount : 0;
                pendingCredit = amount < 0 ? -amount : 0;
                pending = Transaction.builder().date(pendingDate);

            } else if (pending != null && !isAmountOnlyLine(line) && line.length() > 3) {
                pendingDesc = pendingDesc + " " + line;
            }
        }

        if (pending != null) {
            Transaction t = buildDebitCreditTransaction(pending, pendingDesc, pendingDate, pendingDebit, pendingCredit, 0);
            if (t != null) results.add(t);
        }

        log.info("[{}] Parsed {} transactions", bankName(), results.size());
        return results;
    }

    @Override
    public CustomerDetails extractCustomerDetails(String rawText) {
        return CustomerDetails.builder()
            .customerName(extract(rawText, "(?i)card\\s*holder[:\\s]+([A-Za-z ]{3,50})", 1))
            .accountNumber(extract(rawText, "(?i)card\\s*number[:\\s]+([\\dX*\\s]{10,25})", 1))
            .product(extract(rawText, "(?i)card\\s*type[:\\s]+(.+)", 1))
            .email(extract(rawText, "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}", 0))
            .statementPeriod(extract(rawText, "(?i)statement\\s*(period|from)[:\\s]+(.+to.+)", 2))
            .closingBalance(extract(rawText, "(?i)(total\\s*amount\\s*due|outstanding)[:\\s]+([\\d,\\.]+)", 2))
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

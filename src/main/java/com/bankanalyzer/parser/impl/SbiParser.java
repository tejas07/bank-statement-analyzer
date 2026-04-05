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
 * Parses State Bank of India savings/current account statements.
 *
 * Format: DD/MM/YYYY DD/MM/YYYY Description  -|Debit  -|Credit  Balance
 *   '-' in debit or credit column means zero for that field.
 *   Balance uses Indian lakh format (1,01,501.00).
 */
@Slf4j
@Component
@Order(3)
public class SbiParser extends AbstractBankParser {

    private static final Pattern TX_PATTERN = Pattern.compile(
        "^(\\d{2}/\\d{2}/\\d{4})\\s+\\d{2}/\\d{2}/\\d{4}" +
        "\\s+(.*?)" +
        "\\s+(-|[\\d,]+\\.\\d{2})" +   // debit or dash
        "\\s+(-|[\\d,]+\\.\\d{2})" +   // credit or dash
        "\\s+([\\d,]+\\.\\d{2})" +      // balance
        "\\s*$"
    );

    @Override public String bankName()          { return "State Bank of India"; }
    @Override public StatementType statementType() { return StatementType.SAVINGS_ACCOUNT; }

    @Override
    public boolean supports(String rawText) {
        return rawText.contains("State Bank of India") ||
               rawText.contains("SBIN") ||
               (rawText.contains("SBI") && rawText.contains("IFSC"));
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
                pendingDebit   = "-".equals(m.group(3)) ? 0 : parseAmount(m.group(3));
                pendingCredit  = "-".equals(m.group(4)) ? 0 : parseAmount(m.group(4));
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
            .customerName(extract(rawText,
                "(?m)^((?:Mr\\.?|Mrs\\.?|Ms\\.?|Dr\\.?)\\s+[A-Za-z]{2,}(?:\\s+[A-Za-z]{2,}){1,4})(?=\\s+State Bank of India)",
                1, false))
            .accountNumber(extract(rawText, "Account Number\\s*:\\s*([\\d]+)", 1, false))
            .product(extract(rawText, "Product\\s*:\\s*(.+)", 1, true))
            .branch(extract(rawText, "Branch Name\\s*:\\s*(.+)", 1, true))
            .branchCode(extract(rawText, "Branch Code\\s*:\\s*(\\d+)", 1, false))
            .ifscCode(extract(rawText, "IFSC Code\\s*:\\s*([A-Z0-9]+)", 1, false))
            .micrCode(extract(rawText, "MICR Code\\s*:\\s*([\\d]+)", 1, false))
            .cifNumber(extract(rawText, "CIF Number\\s*:\\s*([\\d]+)", 1, false))
            .email(extract(rawText, "Email ID\\s+(\\S+@\\S+)", 1, false))
            .mobile(extract(rawText, "Mobile Number\\s+(\\S+)", 1, false))
            .pan(extract(rawText, "PAN\\s+([A-Z]{5}[0-9]{4}[A-Z])", 1, false))
            .kycStatus(extract(rawText, "KYC Status\\s+(\\S+)", 1, false))
            .segment(extract(rawText, "Segment\\s+(\\S+)", 1, false))
            .accountStatus(extract(rawText, "Account Status\\s*:\\s*(\\S+)", 1, false))
            .accountOpenDate(extract(rawText, "Account open Date\\s*:\\s*([\\d/\\-]+)", 1, false))
            .statementPeriod(extract(rawText, "Statement From\\s*:\\s*(.+to.+)", 1, true))
            .statementDate(extract(rawText, "Date of Statement\\s*:\\s*([\\d\\-]+)", 1, false))
            .closingBalance(extract(rawText, "Clear Balance\\s*:\\s*([\\d,\\.]+(?:CR|DR)?)", 1, false))
            .currency(extract(rawText, "Currency\\s*:\\s*([A-Z]{3})", 1, false))
            .nomineeNam(extract(rawText, "Nominee Name\\s*:\\s*(.+)", 1, true))
            .build();
    }

    /** Extracts group from first regex match. group=0 means full match, group=1 means first group. */
    private String extract(String text, String regex, int group, boolean titleCase) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) return null;
        String val = (group == 0 ? m.group(0) : m.group(group)).trim();
        if (val.isEmpty()) return null;
        return titleCase ? toTitleCase(val) : val;
    }

    private String toTitleCase(String s) {
        String[] words = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                  .append(w.substring(1).toLowerCase()).append(" ");
            }
        }
        return sb.toString().trim();
    }
}

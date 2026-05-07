package com.bankanalyzer.analyzer;

import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.PaymentMode;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.service.CategoryTagger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Enriches raw transactions with payment mode and merchant name,
 * then provides grouping methods for report generation.
 */
@Slf4j
@Service
public class TransactionAnalyzer {

    private final CategoryTagger categoryTagger;

    public TransactionAnalyzer(CategoryTagger categoryTagger) {
        this.categoryTagger = categoryTagger;
    }

    /**
     * Applies payment mode detection, merchant extraction, and category tagging.
     */
    public List<Transaction> analyze(List<Transaction> raw) {
        List<Transaction> enriched = new ArrayList<>();
        for (Transaction t : raw) {
            PaymentMode mode     = detectPaymentMode(t.getDescription());
            String merchant      = extractMerchant(t.getDescription(), mode);
            Category category    = categoryTagger.categorize(t.getDescription());
            enriched.add(Transaction.builder()
                    .date(t.getDate())
                    .description(t.getDescription())
                    .debit(t.getDebit())
                    .credit(t.getCredit())
                    .balance(t.getBalance())
                    .paymentMode(mode)
                    .merchantName(merchant)
                    .category(category)
                    .build());
        }



        log.info("Analyzed {} transactions", enriched.size());
        return enriched;
    }

    /**
     * Detects payment mode from transaction description.
     * Evaluation order matters — specific patterns before general ones.
     */
    @Cacheable(value = "paymentMode", key = "#description")
    public PaymentMode detectPaymentMode(String description) {
        if (description == null) return PaymentMode.OTHER;
        String d = description.toUpperCase().replaceAll("\\s+", " ").trim();

        if (d.contains("UPI") || d.contains("BHIM") || d.contains("PAYTM") ||
                d.contains("PHONEPE") || d.contains("GPAY") || d.contains("GOOGLEPAY") ||
                d.contains("AMAZONPAY") || d.contains("@OKSBI") || d.contains("@YBL") ||
                d.contains("@OKAXIS") || d.contains("@OKHDFCBANK") || d.contains("@PAYTM")) {
            return PaymentMode.UPI;
        }
        if (d.contains("RTGS")) return PaymentMode.RTGS;
        if (d.contains("NEFT")) return PaymentMode.NEFT;
        if (d.contains("IMPS")) return PaymentMode.IMPS;
        if (d.contains("ATM") || d.contains("CASH WDL") || d.contains("CASH WITHDRAWAL") ||
                d.contains("CASHW")) {
            return PaymentMode.ATM;
        }
        if (d.contains("POS") || d.contains("DEBIT CARD") || d.contains("CREDIT CARD") ||
                d.contains("VISA") || d.contains("MASTERCARD") || d.contains("RUPAY") ||
                d.contains("ECOM") || d.contains("MERCHANT") || d.contains("SWIPE")) {
            return PaymentMode.CARD_POS;
        }
        if (d.contains("CHQ") || d.contains("CHEQUE") || d.contains("CTS") || d.contains("CLG")) {
            return PaymentMode.CHEQUE;
        }
        if (d.contains("ECS") || d.contains("NACH") || d.contains("ACH") ||
                d.contains("STANDING INSTRUCTION") || d.contains("EMI") || d.contains("LOAN")) {
            return PaymentMode.ECS_NACH;
        }
        return PaymentMode.OTHER;
    }

    /**
     * Extracts a human-readable merchant name from the description.
     */
    @Cacheable(value = "merchant", key = "#description + #mode.name()")
    public String extractMerchant(String description, PaymentMode mode) {
        if (description == null || description.isBlank()) return "Unknown";

        String d = description.trim();

        // Strip common prefixes (UPI/, NEFT/, etc.)
        d = d.replaceAll("(?i)^(UPI|NEFT|RTGS|IMPS|ATM|POS|ECS|NACH|CHQ|CTS|CLG|ECOM)[/\\-\\s]*", "");

        // Strip reference/transaction IDs (long alphanumeric codes)
        d = d.replaceAll("\\b[A-Z0-9]{10,22}\\b", "");

        // Strip VPA (UPI handles: xxx@yyy)
        d = d.replaceAll("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+", "");

        // Strip dates
        d = d.replaceAll("\\d{2}[/\\-]\\d{2}[/\\-]\\d{4}", "");
        d = d.replaceAll("\\d{2}\\s+[A-Za-z]{3}\\s+\\d{4}", "");

        // For UPI: take the meaningful segment (often after first / or -)
        if (mode == PaymentMode.UPI) {
            String[] parts = d.split("[/\\-]");
            for (String part : parts) {
                String cleaned = part.trim().replaceAll("[^A-Za-z\\s]", "").trim();
                if (cleaned.length() > 2) {
                    return toTitleCase(cleaned);
                }
            }
        }

        // For NEFT/RTGS/IMPS: take first meaningful segment
        if (mode == PaymentMode.NEFT || mode == PaymentMode.RTGS || mode == PaymentMode.IMPS) {
            String[] parts = d.split("[/\\-]");
            for (String part : parts) {
                String cleaned = part.trim().replaceAll("[^A-Za-z\\s]", "").trim();
                if (cleaned.length() > 2) {
                    return toTitleCase(cleaned);
                }
            }
        }

        // General cleanup
        d = d.replaceAll("[^A-Za-z\\s]", " ").replaceAll("\\s+", " ").trim();

        if (d.isEmpty()) return "Unknown";
        // Truncate to 40 chars for readability
        if (d.length() > 40) d = d.substring(0, 40).trim();
        return toTitleCase(d);
    }

    // ---- Grouping methods ----

    public Map<PaymentMode, List<Transaction>> groupByPaymentMode(List<Transaction> txns) {
        Map<PaymentMode, List<Transaction>> map = new LinkedHashMap<>();
        for (PaymentMode mode : PaymentMode.values()) {
            map.put(mode, new ArrayList<>());
        }
        for (Transaction t : txns) {
            map.get(t.getPaymentMode()).add(t);
        }
        // Remove empty modes
        map.entrySet().removeIf(e -> e.getValue().isEmpty());
        return map;
    }

    /**
     * Groups debit transactions by merchant, sorted by total spend descending.
     */
    public Map<String, List<Transaction>> groupByMerchant(List<Transaction> txns) {
        Map<String, List<Transaction>> map = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (t.isDebit()) {
                map.computeIfAbsent(t.getMerchantName(), k -> new ArrayList<>()).add(t);
            }
        }
        // Sort by total debit descending
        return map.entrySet().stream()
                .sorted((a, b) -> Double.compare(totalDebit(b.getValue()), totalDebit(a.getValue())))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * Groups transactions by YearMonth ("yyyy-MM"), in chronological order.
     */
    public TreeMap<String, List<Transaction>> groupByMonth(List<Transaction> txns) {
        TreeMap<String, List<Transaction>> map = new TreeMap<>();
        for (Transaction t : txns) {
            map.computeIfAbsent(t.getMonthKey(), k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    public double totalDebit(List<Transaction> txns) {
        return txns.stream().mapToDouble(Transaction::getDebit).sum();
    }

    public double totalCredit(List<Transaction> txns) {
        return txns.stream().mapToDouble(Transaction::getCredit).sum();
    }

    private String toTitleCase(String s) {
        String[] words = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                  .append(w.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}

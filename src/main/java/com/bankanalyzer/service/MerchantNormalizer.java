package com.bankanalyzer.service;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Strips UPI noise, transaction IDs, and bank prefixes from raw merchant descriptions
 * before BK-Tree lookup, so "UPI/SWIGGY*ORDER12345/ICICI" becomes "SWIGGY".
 */
public class MerchantNormalizer {

    // Patterns stripped in order before lookup
    private static final Pattern[] STRIP_PATTERNS = {
        Pattern.compile("(?i)\\bUPI[-/]?"),           // UPI/ or UPI-
        Pattern.compile("(?i)\\bIMPS[-/]?"),
        Pattern.compile("(?i)\\bNEFT[-/]?"),
        Pattern.compile("(?i)\\bRTGS[-/]?"),
        Pattern.compile("(?i)\\bPOS\\s+"),            // POS prefix
        Pattern.compile("(?i)\\bINB\\s+"),            // INB prefix
        Pattern.compile("[0-9]{6,}"),                  // long numeric IDs
        Pattern.compile("/[A-Z0-9]{8,}"),              // /REFCODE after merchant
        Pattern.compile("\\*[A-Z0-9]+"),               // *ORDER123 after merchant
        Pattern.compile("@[A-Z0-9]+"),                 // @upihandle
        Pattern.compile("[^A-Z0-9\\s]"),               // remaining special chars
        Pattern.compile("\\s{2,}"),                    // collapse whitespace
    };

    // Common abbreviations seen in Indian bank statements
    private static final Map<String, String> EXPANSIONS = Map.ofEntries(
        Map.entry("SWGGY",   "SWIGGY"),
        Map.entry("ZMTO",    "ZOMATO"),
        Map.entry("AMZN",    "AMAZON"),
        Map.entry("AMZNINPAY", "AMAZON"),
        Map.entry("FKRT",    "FLIPKART"),
        Map.entry("NMTR",    "MYNTRA"),
        Map.entry("NFLX",    "NETFLIX"),
        Map.entry("NTFLX",   "NETFLIX"),
        Map.entry("SPTFY",   "SPOTIFY"),
        Map.entry("IRCTC",   "IRCTC"),
        Map.entry("BBSTAR",  "BIGBASKET"),
        Map.entry("BLNKT",   "BLINKIT"),
        Map.entry("ZPTO",    "ZEPTO"),
        Map.entry("PHRMESY", "PHARMEASY"),
        Map.entry("NETMDS",  "NETMEDS"),
        Map.entry("PRACTO",  "PRACTO"),
        Map.entry("AIRTEL",  "AIRTEL"),
        Map.entry("GRWW",    "GROWW")
    );

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "";

        String s = raw.toUpperCase().trim();

        // Apply strip patterns sequentially
        for (Pattern p : STRIP_PATTERNS) {
            s = p.matcher(s).replaceAll(" ");
        }
        s = s.trim();

        // Expand known abbreviations (check each token)
        String[] tokens = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            sb.append(EXPANSIONS.getOrDefault(token, token)).append(" ");
        }
        s = sb.toString().trim();

        // Return first meaningful token if result is multi-word (merchant name is usually first word)
        String[] parts = s.split("\\s+");
        return parts.length > 0 ? parts[0] : s;
    }
}

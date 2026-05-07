package com.bankanalyzer.service;

import com.bankanalyzer.model.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class CategoryTagger {

    private static final Map<Category, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(Category.SALARY_INCOME, List.of(
            "SALARY", "STIPEND", "PAYROLL", "WAGES", "INCOME"
        ));
        KEYWORDS.put(Category.EMI_LOAN, List.of(
            "EMI", "LOAN", "LIC", "INSURANCE", "PREMIUM", "NACH", "ECS", "REPAYMENT"
        ));
        KEYWORDS.put(Category.INVESTMENT, List.of(
            "MUTUAL FUND", "SIP", "ZERODHA", "GROWW", "NAVI", "KUVERA", "COIN",
            "SMALLCASE", "STOCKS", "DEMAT"
        ));
        KEYWORDS.put(Category.FUEL, List.of(
            "FUEL", "PETROL", "DIESEL", "HP ", "HPCL", "IOCL", "BPCL", "IOC",
            "RELIANCE BP", "RELIANCE PETRO", "SHELL", "NAYARA", "ESSAR OIL"
        ));
        KEYWORDS.put(Category.TRAVEL, List.of(
            "IRCTC", "REDBUS", "RAILYATRI", "OLA", "UBER", "RAPIDO",
            "MAKEMYTRIP", "GOIBIBO", "YATRA", "CLEARTRIP", "INDIGO",
            "AIR INDIA", "SPICEJET", "VISTARA", "AKASA", "AIRPORT", "METRO CARD"
        ));
        KEYWORDS.put(Category.FOOD_DINING, List.of(
            "SWIGGY", "ZOMATO", "DOMINO", "PIZZA", "MCDONALD", "KFC", "SUBWAY",
            "BURGER KING", "CAFE", "RESTAURANT", "HOTEL", "BARBEQUE", "DHABA",
            "FOOD", "DINING", "CHAI", "BAKERY"
        ));
        KEYWORDS.put(Category.GROCERIES, List.of(
            "BIGBASKET", "BLINKIT", "ZEPTO", "DUNZO", "DMART", "D-MART",
            "RELIANCE FRESH", "MORE RETAIL", "GROFERS", "INSTAMART",
            "SUPERMARKET", "GROCERY", "VEGETABLES", "KIRANA"
        ));
        KEYWORDS.put(Category.HEALTH, List.of(
            "PHARMACY", "MEDPLUS", "APOLLO", "FORTIS", "MANIPAL", "MAX HOSPITAL",
            "HOSPITAL", "CLINIC", "DIAGNOSTIC", "LAB", "MEDICAL", "MEDICINE",
            "PRACTO", "PHARMEASY", "NETMEDS", "1MG"
        ));
        KEYWORDS.put(Category.ENTERTAINMENT, List.of(
            "NETFLIX", "SPOTIFY", "PRIME VIDEO", "HOTSTAR", "ZEE5", "SONY LIV",
            "BOOKMYSHOW", "PVR", "INOX", "CINEPOLIS", "GAMING", "STEAM",
            "YOUTUBE PREMIUM", "APPLE MUSIC", "GAANA", "JIO CINEMA"
        ));
        KEYWORDS.put(Category.UTILITIES, List.of(
            "ELECTRICITY", "BESCOM", "MSEB", "TPDDL", "WATER BILL", "GAS",
            "BROADBAND", "INTERNET", "AIRTEL", "JIO", "BSNL", "VI ", "VODAFONE",
            "IDEA", "TATA SKY", "DTH", "RECHARGE", "MOBILE BILL", "UTILITY"
        ));
        KEYWORDS.put(Category.EDUCATION, List.of(
            "SCHOOL FEE", "COLLEGE FEE", "TUITION", "UDEMY", "COURSERA",
            "BYJU", "UNACADEMY", "VEDANTU", "EDUCATION", "UNIVERSITY", "FEE"
        ));
        KEYWORDS.put(Category.SHOPPING, List.of(
            "AMAZON", "FLIPKART", "MYNTRA", "MEESHO", "SNAPDEAL", "AJIO",
            "NYKAA", "TATA CLiQ", "RELIANCE DIGITAL", "CROMA", "DECATHLON",
            "H&M", "ZARA", "WESTSIDE", "LIFESTYLE", "SHOPPERS STOP"
        ));
        KEYWORDS.put(Category.TRANSFER, List.of(
            "SELF TRANSFER", "OWN ACCOUNT", "TRANSFER TO", "TRANSFER FROM"
        ));
    }

    // BK-Tree built once at startup from keyword → category mappings
    private static final BKTree BK_TREE = new BKTree();

    // Max edit distance — scales with token length to avoid short-word false positives
    private static final int BASE_THRESHOLD = 2;

    static {
        KEYWORDS.forEach((category, keywords) ->
            keywords.forEach(kw -> BK_TREE.insert(kw.trim(), category))
        );
        log.info("BKTree built with {} keyword entries", KEYWORDS.values().stream().mapToInt(List::size).sum());
    }

    @Cacheable(value = "category", key = "#description")
    public Category categorize(String description) {
        if (description == null || description.isBlank()) return Category.OTHER;
        String upper = description.toUpperCase();

        // Fast path: exact substring match (handles clean merchant names)
        for (Map.Entry<Category, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (upper.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        // Fuzzy path: normalize merchant name then BK-Tree lookup
        String normalized = MerchantNormalizer.normalize(upper);
        if (normalized.isBlank()) return Category.OTHER;

        int threshold = computeThreshold(normalized);
        Optional<Category> fuzzy = BK_TREE.search(normalized, threshold);
        if (fuzzy.isPresent()) {
            log.debug("BKTree matched '{}' → {}", normalized, fuzzy.get());
            return fuzzy.get();
        }

        return Category.OTHER;
    }

    // Longer tokens tolerate more edit distance; short tokens (≤4 chars) allow only 1
    private static int computeThreshold(String token) {
        int len = token.length();
        if (len <= 4) return 1;
        if (len <= 7) return BASE_THRESHOLD;
        return BASE_THRESHOLD + 1;
    }
}

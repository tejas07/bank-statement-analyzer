package com.bankanalyzer.analyzer;

import com.bankanalyzer.model.PaymentMode;
import com.bankanalyzer.service.CategoryTagger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests pinning current {@link TransactionAnalyzer} behavior
 * (Phase 0 safety net) for the description patterns already exercised in production data.
 */
public class TransactionAnalyzerTest {

    private final TransactionAnalyzer analyzer = new TransactionAnalyzer(new CategoryTagger());

    @Test
    void detectsPaymentModes() {
        assertEquals(PaymentMode.UPI, analyzer.detectPaymentMode("UPI/CR/310742380234/MR TEJAS"));
        assertEquals(PaymentMode.NEFT, analyzer.detectPaymentMode("NEFT/CITIN00000123/JOHN SMITH"));
        assertEquals(PaymentMode.RTGS, analyzer.detectPaymentMode("RTGS/XYZ12345/JANE DOE"));
        assertEquals(PaymentMode.IMPS, analyzer.detectPaymentMode("IMPS/P2A/912345678901/ACME CORP"));
        assertEquals(PaymentMode.ATM, analyzer.detectPaymentMode("ATM WDL ATM CASH SBI"));
        assertEquals(PaymentMode.CARD_POS, analyzer.detectPaymentMode("POS PURCHASE AMAZON RETAIL"));
        assertEquals(PaymentMode.CHEQUE, analyzer.detectPaymentMode("CHQ ISSUED 001234"));
        assertEquals(PaymentMode.ECS_NACH, analyzer.detectPaymentMode("ECS NACH DEBIT LIC PREMIUM"));
        assertEquals(PaymentMode.OTHER, analyzer.detectPaymentMode("RANDOM TEXT WITH NO KEYWORDS"));
        assertEquals(PaymentMode.OTHER, analyzer.detectPaymentMode(null));
    }

    @Test
    void extractsMerchantFromUpiDescription() {
        assertEquals("Mr Tejas",
                analyzer.extractMerchant("UPI/CR/310742380234/MR TEJAS", PaymentMode.UPI));
    }

    @Test
    void extractsMerchantFromNeftDescription() {
        assertEquals("John Smith",
                analyzer.extractMerchant("NEFT/CITIN00000123/JOHN SMITH/HDFC0001234", PaymentMode.NEFT));
    }

    @Test
    void extractsMerchantFromAtmDescription() {
        assertEquals("Wdl Atm Cash Sbi",
                analyzer.extractMerchant("ATM WDL ATM CASH SBI", PaymentMode.ATM));
    }

    @Test
    void extractsMerchantFromPosDescription() {
        assertEquals("Purchase Amazon Retail",
                analyzer.extractMerchant("POS PURCHASE AMAZON RETAIL", PaymentMode.CARD_POS));
    }

    @Test
    void extractMerchantHandlesNullAndBlank() {
        assertEquals("Unknown", analyzer.extractMerchant(null, PaymentMode.OTHER));
        assertEquals("Unknown", analyzer.extractMerchant("   ", PaymentMode.OTHER));
    }
}

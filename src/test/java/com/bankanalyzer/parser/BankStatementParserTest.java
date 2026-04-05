package com.bankanalyzer.parser;

import com.bankanalyzer.parser.impl.IciciCreditCardParser;
import com.bankanalyzer.parser.impl.SbiParser;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BankStatementParserTest {

    private final IciciCreditCardParser iciciCcParser = new IciciCreditCardParser();
    private final SbiParser sbiParser = new SbiParser();

    private static final String[] ICICI_CC_LINES = {
        "30-MAY-24 Autodebit Payment Recd. 0.00 -4,760.89",
        "04-APR-24 74332744097409596656227 RELIANCE BP MOBILITY L HASSAN IN 0.00 3,083.96",
        "06-APR-24 Reversal of fuel Surcharge 0.00 -30.53",
        "12-APR-24 IGST-Rev-CI@18% 0.00 -1.80",
        "30-APR-24 240833409283 INFINITY PAYMENT RECEIVED, THANK YOU 0.00 -9,211.79",
        "30-MAY-24 SR986556300 CREDIT BALANCE REFUND 0.00 4,760.89"
    };

    private static final String[] SBI_LINES = {
        "14/01/2022 14/01/2022 DEBIT ATMCard AMC - 147.50 27,089.50",
        "17/04/2023 17/04/2023 ATM WDL ATM CASH SBI - 5,000.00 1,927.00",
        "17/04/2023 17/04/2023 UPI/CR/310742380234/MR TEJAS - 5,000.00 6,927.00"
    };

    @Test
    void testIciciCcParserDetectsFormat() {
        String text = "ICICI Bank Credit Card Statement\n" + String.join("\n", ICICI_CC_LINES);
        System.out.println("ICICI CC supports: " + iciciCcParser.supports(text));
        System.out.println("Statement type: " + iciciCcParser.statementType());
    }

    @Test
    void testIciciCcParseLines() {
        String text = String.join("\n", ICICI_CC_LINES);
        var transactions = iciciCcParser.parse(text);
        System.out.println("ICICI CC parsed: " + transactions.size() + " transactions");
        transactions.forEach(t ->
            System.out.printf("  %s | %-45s | debit=%8.2f | credit=%8.2f%n",
                t.getDate(), t.getDescription(), t.getDebit(), t.getCredit()));
    }

    @Test
    void testSbiParseLines() {
        String text = String.join("\n", SBI_LINES);
        var transactions = sbiParser.parse(text);
        System.out.println("SBI parsed: " + transactions.size() + " transactions");
        transactions.forEach(t ->
            System.out.printf("  %s | %-45s | debit=%8.2f | credit=%8.2f | balance=%10.2f%n",
                t.getDate(), t.getDescription(), t.getDebit(), t.getCredit(), t.getBalance()));
    }

    @Test
    void testParseDate() {
        System.out.println("=== parseDate test ===");
        String[] dates = {"30-MAY-24", "04-APR-24", "14/01/2022", "17/04/2023"};
        for (String d : dates) {
            System.out.printf("parseDate('%s') = %s%n", d, AbstractBankParser.parseDate(d));
        }
    }
}

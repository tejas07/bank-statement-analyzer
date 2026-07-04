package com.bankanalyzer.parser;

import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.parser.impl.IciciCreditCardParser;
import com.bankanalyzer.parser.impl.SbiParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests pinning current parser output (Phase 0 safety net).
 * These values were captured from the actual parser output before refactoring —
 * they must keep passing unchanged through every later refactor phase.
 */
public class BankStatementParserTest {

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
    private final IciciCreditCardParser iciciCcParser = new IciciCreditCardParser();
    private final SbiParser sbiParser = new SbiParser();

    @Test
    void testIciciCcParserDetectsFormat() {
        String text = "ICICI Bank Credit Card Statement\n" + String.join("\n", ICICI_CC_LINES);
        assertTrue(iciciCcParser.supports(text));
        assertEquals(com.bankanalyzer.model.StatementType.CREDIT_CARD, iciciCcParser.statementType());
    }

    @Test
    void testIciciCcParseLines() {
        String text = String.join("\n", ICICI_CC_LINES);
        List<Transaction> transactions = iciciCcParser.parse(text);

        assertEquals(6, transactions.size());

        assertTransaction(transactions.get(0), LocalDate.of(2024, 5, 30),
                "Autodebit Payment Recd.", 0.00, 4760.89);
        assertTransaction(transactions.get(1), LocalDate.of(2024, 4, 4),
                "RELIANCE BP MOBILITY L HASSAN IN", 3083.96, 0.00);
        assertTransaction(transactions.get(2), LocalDate.of(2024, 4, 6),
                "Reversal of fuel Surcharge", 0.00, 30.53);
        assertTransaction(transactions.get(3), LocalDate.of(2024, 4, 12),
                "IGST-Rev-CI@18%", 0.00, 1.80);
        assertTransaction(transactions.get(4), LocalDate.of(2024, 4, 30),
                "INFINITY PAYMENT RECEIVED, THANK YOU", 0.00, 9211.79);
        assertTransaction(transactions.get(5), LocalDate.of(2024, 5, 30),
                "CREDIT BALANCE REFUND", 4760.89, 0.00);
    }

    private void assertTransaction(Transaction t, LocalDate date, String description,
                                   double debit, double credit) {
        assertEquals(date, t.getDate());
        assertEquals(description, t.getDescription());
        assertEquals(debit, t.getDebit(), 0.001);
        assertEquals(credit, t.getCredit(), 0.001);
    }

    @Test
    void testSbiParseLines() {
        String text = String.join("\n", SBI_LINES);
        List<Transaction> transactions = sbiParser.parse(text);

        assertEquals(3, transactions.size());

        assertEquals(LocalDate.of(2022, 1, 14), transactions.get(0).getDate());
        assertEquals("DEBIT ATMCard AMC", transactions.get(0).getDescription());
        assertEquals(0.00, transactions.get(0).getDebit(), 0.001);
        assertEquals(147.50, transactions.get(0).getCredit(), 0.001);
        assertEquals(27089.50, transactions.get(0).getBalance(), 0.001);

        assertEquals(LocalDate.of(2023, 4, 17), transactions.get(1).getDate());
        assertEquals("ATM WDL ATM CASH SBI", transactions.get(1).getDescription());
        assertEquals(0.00, transactions.get(1).getDebit(), 0.001);
        assertEquals(5000.00, transactions.get(1).getCredit(), 0.001);
        assertEquals(1927.00, transactions.get(1).getBalance(), 0.001);

        assertEquals(LocalDate.of(2023, 4, 17), transactions.get(2).getDate());
        assertEquals("UPI/CR/310742380234/MR TEJAS", transactions.get(2).getDescription());
        assertEquals(0.00, transactions.get(2).getDebit(), 0.001);
        assertEquals(5000.00, transactions.get(2).getCredit(), 0.001);
        assertEquals(6927.00, transactions.get(2).getBalance(), 0.001);
    }

    @Test
    void testParseDate() {
        assertEquals(LocalDate.of(2024, 5, 30), AbstractBankParser.parseDate("30-MAY-24"));
        assertEquals(LocalDate.of(2024, 4, 4), AbstractBankParser.parseDate("04-APR-24"));
        assertEquals(LocalDate.of(2022, 1, 14), AbstractBankParser.parseDate("14/01/2022"));
        assertEquals(LocalDate.of(2023, 4, 17), AbstractBankParser.parseDate("17/04/2023"));
    }
}

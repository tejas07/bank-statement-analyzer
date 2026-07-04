package com.bankanalyzer.report;

import com.bankanalyzer.api.dto.CustomerDetails;
import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.PaymentMode;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.report.excel.CustomerDetailsSheetWriter;
import com.bankanalyzer.report.excel.ExcelChartBuilder;
import com.bankanalyzer.report.excel.ExcelStyleFactory;
import com.bankanalyzer.report.excel.MerchantSheetWriter;
import com.bankanalyzer.report.excel.MonthSheetWriter;
import com.bankanalyzer.report.excel.PaymentModeSheetWriter;
import com.bankanalyzer.report.excel.TransactionsSheetWriter;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural smoke tests pinning current {@link ExcelReportGenerator} output
 * (Phase 0 safety net) — sheet names/count must stay stable across the Phase 1.5
 * per-section-writer split.
 */
public class ExcelReportGeneratorTest {

    private final ExcelStyleFactory styleFactory = new ExcelStyleFactory();
    private final ExcelChartBuilder chartBuilder = new ExcelChartBuilder();

    private final ExcelReportGenerator generator = new ExcelReportGenerator(
            new CustomerDetailsSheetWriter(styleFactory),
            new TransactionsSheetWriter(styleFactory),
            new PaymentModeSheetWriter(styleFactory, chartBuilder),
            new MerchantSheetWriter(styleFactory, chartBuilder),
            new MonthSheetWriter(styleFactory, chartBuilder));

    @Test
    void generatesFourSheetsWithoutCustomerDetails() throws Exception {
        byte[] bytes = generator.generateBytes(fixture());
        assertTrue(bytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(4, wb.getNumberOfSheets());
            assertEquals("All Transactions", wb.getSheetAt(0).getSheetName());
            assertEquals("By Payment Mode", wb.getSheetAt(1).getSheetName());
            assertEquals("By Merchant", wb.getSheetAt(2).getSheetName());
            assertEquals("By Month", wb.getSheetAt(3).getSheetName());

            XSSFSheet txnSheet = wb.getSheetAt(0);
            // header row + 3 transaction rows
            assertEquals(3, txnSheet.getLastRowNum());
        }
    }

    private List<Transaction> fixture() {
        return List.of(
                Transaction.builder().date(LocalDate.of(2024, 1, 5)).description("Grocery run")
                        .debit(1500).credit(0).balance(50000)
                        .paymentMode(PaymentMode.UPI).merchantName("Bigbasket").category(Category.GROCERIES).build(),
                Transaction.builder().date(LocalDate.of(2024, 1, 10)).description("Salary")
                        .debit(0).credit(80000).balance(130000)
                        .paymentMode(PaymentMode.NEFT).merchantName("Employer").category(Category.SALARY_INCOME).build(),
                Transaction.builder().date(LocalDate.of(2024, 2, 3)).description("Fuel")
                        .debit(2000).credit(0).balance(128000)
                        .paymentMode(PaymentMode.CARD_POS).merchantName("HPCL").category(Category.FUEL).build()
        );
    }

    @Test
    void generatesFiveSheetsWithCustomerDetails() throws Exception {
        CustomerDetails cd = CustomerDetails.builder()
                .customerName("Jane Doe").accountNumber("1234567890").build();

        byte[] bytes = generator.generateBytes(fixture(), cd);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(5, wb.getNumberOfSheets());
            assertEquals("Customer Details", wb.getSheetAt(0).getSheetName());
            assertEquals("All Transactions", wb.getSheetAt(1).getSheetName());
        }
    }
}

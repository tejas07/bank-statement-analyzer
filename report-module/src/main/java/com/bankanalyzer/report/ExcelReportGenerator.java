package com.bankanalyzer.report;

import com.bankanalyzer.api.dto.CustomerDetails;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.TransactionGroups;
import com.bankanalyzer.report.excel.CustomerDetailsSheetWriter;
import com.bankanalyzer.report.excel.MerchantSheetWriter;
import com.bankanalyzer.report.excel.MonthSheetWriter;
import com.bankanalyzer.report.excel.PaymentModeSheetWriter;
import com.bankanalyzer.report.excel.TransactionsSheetWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Orchestrates a 4-5 sheet XLSX report from enriched transactions using Apache POI 5.x.
 * <p>
 * Sheet 0 (optional): Customer Details
 * Sheet 1: All Transactions
 * Sheet 2: By Payment Mode  (table + pie chart)
 * Sheet 3: By Merchant      (table + pie chart)
 * Sheet 4: By Month         (table + pie chart)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelReportGenerator {

    private final CustomerDetailsSheetWriter customerDetailsSheetWriter;
    private final TransactionsSheetWriter transactionsSheetWriter;
    private final PaymentModeSheetWriter paymentModeSheetWriter;
    private final MerchantSheetWriter merchantSheetWriter;
    private final MonthSheetWriter monthSheetWriter;

    /**
     * Writes the XLSX report to a file (used by the CLI).
     */
    public void generate(List<Transaction> transactions, File outputFile) throws IOException {
        log.info("Generating report -> {}", outputFile);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(generateBytes(transactions));
        }
        log.info("Report saved: {}", outputFile.getAbsolutePath());
    }

    /**
     * Builds the XLSX workbook in memory and returns the raw bytes.
     * Use this for HTTP responses so Content-Length can be set correctly.
     */
    public byte[] generateBytes(List<Transaction> transactions) throws IOException {
        return generateBytes(transactions, null);
    }

    public byte[] generateBytes(List<Transaction> transactions,
                                CustomerDetails customerDetails) throws IOException {
        log.info("Generating report with {} transactions", transactions.size());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            if (customerDetails != null) customerDetailsSheetWriter.write(wb, customerDetails);
            transactionsSheetWriter.write(wb, transactions);
            paymentModeSheetWriter.write(wb, TransactionGroups.groupByPaymentMode(transactions));
            merchantSheetWriter.write(wb, TransactionGroups.groupByMerchant(transactions));
            monthSheetWriter.write(wb, TransactionGroups.groupByMonth(transactions));
            wb.write(bos);
        }
        return bos.toByteArray();
    }
}

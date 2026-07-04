package com.bankanalyzer.report.excel;

import com.bankanalyzer.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Sheet 1: All Transactions.
 */
@Component
@RequiredArgsConstructor
public class TransactionsSheetWriter {

    private final ExcelStyleFactory styleFactory;

    public void write(XSSFWorkbook wb, List<Transaction> transactions) {
        XSSFSheet sheet = wb.createSheet("All Transactions");
        sheet.createFreezePane(0, 1);

        XSSFCellStyle headerStyle = styleFactory.createHeaderStyle(wb);
        XSSFCellStyle currencyStyle = styleFactory.createCurrencyStyle(wb);
        XSSFCellStyle dateStyle = styleFactory.createDateStyle(wb);
        XSSFCellStyle altRowStyle = styleFactory.createAltRowStyle(wb);

        // Header row
        String[] headers = {"Date", "Description", "Payment Mode", "Merchant", "Debit", "Credit", "Balance"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        int rowNum = 1;
        for (Transaction t : transactions) {
            Row row = sheet.createRow(rowNum);
            boolean alt = rowNum % 2 == 0;

            Cell dateCell = row.createCell(0);
            if (t.getDate() != null) {
                dateCell.setCellValue(toJavaUtilDate(t.getDate()));
                dateCell.setCellStyle(dateStyle);
            }

            Cell descCell = row.createCell(1);
            descCell.setCellValue(t.getDescription());
            if (alt) descCell.setCellStyle(altRowStyle);

            Cell modeCell = row.createCell(2);
            modeCell.setCellValue(t.getPaymentMode().getLabel());
            if (alt) modeCell.setCellStyle(altRowStyle);

            Cell merchantCell = row.createCell(3);
            merchantCell.setCellValue(t.getMerchantName());
            if (alt) merchantCell.setCellStyle(altRowStyle);

            Cell debitCell = row.createCell(4);
            debitCell.setCellValue(t.getDebit());
            debitCell.setCellStyle(currencyStyle);

            Cell creditCell = row.createCell(5);
            creditCell.setCellValue(t.getCredit());
            creditCell.setCellStyle(currencyStyle);

            Cell balanceCell = row.createCell(6);
            balanceCell.setCellValue(t.getBalance());
            balanceCell.setCellStyle(currencyStyle);

            rowNum++;
        }

        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 50 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 30 * 256);
        sheet.setColumnWidth(4, 14 * 256);
        sheet.setColumnWidth(5, 14 * 256);
        sheet.setColumnWidth(6, 16 * 256);
    }

    private Date toJavaUtilDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}

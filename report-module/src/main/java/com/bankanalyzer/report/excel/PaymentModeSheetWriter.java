package com.bankanalyzer.report.excel;

import com.bankanalyzer.model.PaymentMode;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.TransactionGroups;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Sheet 2: By Payment Mode (table + pie chart).
 */
@Component
@RequiredArgsConstructor
public class PaymentModeSheetWriter {

    private final ExcelStyleFactory styleFactory;
    private final ExcelChartBuilder chartBuilder;

    public void write(XSSFWorkbook wb, Map<PaymentMode, List<Transaction>> grouped) {
        XSSFSheet sheet = wb.createSheet("By Payment Mode");

        XSSFCellStyle headerStyle = styleFactory.createHeaderStyle(wb);
        XSSFCellStyle currencyStyle = styleFactory.createCurrencyStyle(wb);
        XSSFCellStyle numberStyle = styleFactory.createNumberStyle(wb);

        // Header
        String[] headers = {"Payment Mode", "Txn Count", "Total Debit", "Total Credit"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Map.Entry<PaymentMode, List<Transaction>> entry : grouped.entrySet()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey().getLabel());

            Cell countCell = row.createCell(1);
            countCell.setCellValue(entry.getValue().size());
            countCell.setCellStyle(numberStyle);

            Cell debitCell = row.createCell(2);
            debitCell.setCellValue(TransactionGroups.totalDebit(entry.getValue()));
            debitCell.setCellStyle(currencyStyle);

            Cell creditCell = row.createCell(3);
            creditCell.setCellValue(TransactionGroups.totalCredit(entry.getValue()));
            creditCell.setCellStyle(currencyStyle);
        }

        int dataLastRow = rowNum - 1;

        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 16 * 256);

        // Pie chart: payment mode labels (col 0) vs total debit (col 2)
        if (dataLastRow >= 1) {
            chartBuilder.createPieChart(sheet, 0, 2, 1, dataLastRow,
                    5, 0, 13, 20, "Spend by Payment Mode");
        }
    }
}

package com.bankanalyzer.report.excel;

import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.TransactionGroups;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sheet 3: By Merchant (top-N table + pie chart).
 */
@Component
@RequiredArgsConstructor
public class MerchantSheetWriter {

    private static final int TOP_N_MERCHANTS = 10;

    private final ExcelStyleFactory styleFactory;
    private final ExcelChartBuilder chartBuilder;

    public void write(XSSFWorkbook wb, Map<String, List<Transaction>> grouped) {
        XSSFSheet sheet = wb.createSheet("By Merchant");

        XSSFCellStyle headerStyle = styleFactory.createHeaderStyle(wb);
        XSSFCellStyle currencyStyle = styleFactory.createCurrencyStyle(wb);
        XSSFCellStyle numberStyle = styleFactory.createNumberStyle(wb);

        // Header
        String[] headers = {"Merchant", "Txn Count", "Total Debit"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        List<Map.Entry<String, List<Transaction>>> entries = new ArrayList<>(grouped.entrySet());

        // Write top N merchants
        int rowNum = 1;
        double otherTotal = 0;
        int otherCount = 0;

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, List<Transaction>> entry = entries.get(i);
            if (i < TOP_N_MERCHANTS) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(entry.getKey());

                Cell countCell = row.createCell(1);
                countCell.setCellValue(entry.getValue().size());
                countCell.setCellStyle(numberStyle);

                Cell debitCell = row.createCell(2);
                debitCell.setCellValue(TransactionGroups.totalDebit(entry.getValue()));
                debitCell.setCellStyle(currencyStyle);
            } else {
                otherTotal += TransactionGroups.totalDebit(entry.getValue());
                otherCount += entry.getValue().size();
            }
        }

        // "Others" row
        if (otherTotal > 0) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue("Others");
            Cell countCell = row.createCell(1);
            countCell.setCellValue(otherCount);
            countCell.setCellStyle(numberStyle);
            Cell debitCell = row.createCell(2);
            debitCell.setCellValue(otherTotal);
            debitCell.setCellStyle(currencyStyle);
        }

        int dataLastRow = rowNum - 1;

        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 16 * 256);

        if (dataLastRow >= 1) {
            chartBuilder.createPieChart(sheet, 0, 2, 1, dataLastRow,
                    4, 0, 13, 22, "Top Merchants by Spend");
        }
    }
}

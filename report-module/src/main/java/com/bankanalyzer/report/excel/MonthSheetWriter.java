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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sheet 4: By Month (table + pie chart).
 */
@Component
@RequiredArgsConstructor
public class MonthSheetWriter {

    private final ExcelStyleFactory styleFactory;
    private final ExcelChartBuilder chartBuilder;

    public void write(XSSFWorkbook wb, TreeMap<String, List<Transaction>> grouped) {
        XSSFSheet sheet = wb.createSheet("By Month");

        XSSFCellStyle headerStyle = styleFactory.createHeaderStyle(wb);
        XSSFCellStyle currencyStyle = styleFactory.createCurrencyStyle(wb);
        XSSFCellStyle numberStyle = styleFactory.createNumberStyle(wb);

        // Header
        String[] headers = {"Month", "Debit Count", "Total Debit", "Credit Count", "Total Credit"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> txns = entry.getValue();
            long debitCount = txns.stream().filter(Transaction::isDebit).count();
            long creditCount = txns.stream().filter(Transaction::isCredit).count();

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());

            Cell dc = row.createCell(1);
            dc.setCellValue(debitCount);
            dc.setCellStyle(numberStyle);

            Cell td = row.createCell(2);
            td.setCellValue(TransactionGroups.totalDebit(txns));
            td.setCellStyle(currencyStyle);

            Cell cc = row.createCell(3);
            cc.setCellValue(creditCount);
            cc.setCellStyle(numberStyle);

            Cell tc = row.createCell(4);
            tc.setCellValue(TransactionGroups.totalCredit(txns));
            tc.setCellStyle(currencyStyle);
        }

        int dataLastRow = rowNum - 1;

        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        sheet.setColumnWidth(4, 16 * 256);

        if (dataLastRow >= 1) {
            chartBuilder.createPieChart(sheet, 0, 2, 1, dataLastRow,
                    6, 0, 14, 22, "Monthly Spend Distribution");
        }
    }
}

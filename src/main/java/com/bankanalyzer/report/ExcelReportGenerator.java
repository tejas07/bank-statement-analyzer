package com.bankanalyzer.report;

import com.bankanalyzer.analyzer.TransactionAnalyzer;
import com.bankanalyzer.api.dto.CustomerDetails;
import com.bankanalyzer.model.PaymentMode;
import com.bankanalyzer.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates a 4-sheet XLSX report from enriched transactions using Apache POI 5.x.
 *
 * Sheet 1: All Transactions
 * Sheet 2: By Payment Mode  (table + pie chart)
 * Sheet 3: By Merchant      (table + pie chart)
 * Sheet 4: By Month         (table + pie chart)
 */
@Slf4j
@Component
public class ExcelReportGenerator {

    private static final int TOP_N_MERCHANTS = 10;

    private final TransactionAnalyzer analyzer;

    public ExcelReportGenerator(TransactionAnalyzer analyzer) {
        this.analyzer = analyzer;
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
            if (customerDetails != null) writeCustomerDetailsSheet(wb, customerDetails);
            writeAllTransactionsSheet(wb, transactions);
            writeByPaymentModeSheet(wb, analyzer.groupByPaymentMode(transactions));
            writeByMerchantSheet(wb, analyzer.groupByMerchant(transactions));
            writeByMonthSheet(wb, analyzer.groupByMonth(transactions));
            wb.write(bos);
        }
        return bos.toByteArray();
    }

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

    // -------------------------------------------------------------------------
    // Sheet 0: Customer Details (optional)
    // -------------------------------------------------------------------------

    private void writeCustomerDetailsSheet(XSSFWorkbook wb, CustomerDetails cd) {
        XSSFSheet sheet = wb.createSheet("Customer Details");
        XSSFCellStyle headerStyle = createHeaderStyle(wb);
        XSSFCellStyle labelStyle  = createLabelStyle(wb);

        // Title row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Account & Customer Information");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        String[][] rows = {
            {"Customer Name",     cd.getCustomerName()},
            {"Account Number",    cd.getAccountNumber()},
            {"Product",           cd.getProduct()},
            {"Account Status",    cd.getAccountStatus()},
            {"Account Open Date", cd.getAccountOpenDate()},
            {"Branch",            cd.getBranch()},
            {"Branch Code",       cd.getBranchCode()},
            {"IFSC Code",         cd.getIfscCode()},
            {"MICR Code",         cd.getMicrCode()},
            {"CIF Number",        cd.getCifNumber()},
            {"Email",             cd.getEmail()},
            {"Mobile",            cd.getMobile()},
            {"PAN",               cd.getPan()},
            {"KYC Status",        cd.getKycStatus()},
            {"Segment",           cd.getSegment()},
            {"Currency",          cd.getCurrency()},
            {"Closing Balance",   cd.getClosingBalance()},
            {"Statement Period",  cd.getStatementPeriod()},
            {"Statement Date",    cd.getStatementDate()},
            {"Nominee",           cd.getNomineeNam()},
        };

        int rowNum = 2;
        for (String[] pair : rows) {
            if (pair[1] == null || pair[1].isBlank()) continue;
            Row row = sheet.createRow(rowNum++);
            Cell labelCell = row.createCell(0);
            labelCell.setCellValue(pair[0]);
            labelCell.setCellStyle(labelStyle);
            row.createCell(1).setCellValue(pair[1]);
        }

        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 40 * 256);
    }

    // -------------------------------------------------------------------------
    // Sheet 1: All Transactions
    // -------------------------------------------------------------------------

    private void writeAllTransactionsSheet(XSSFWorkbook wb, List<Transaction> transactions) {
        XSSFSheet sheet = wb.createSheet("All Transactions");
        sheet.createFreezePane(0, 1);

        XSSFCellStyle headerStyle = createHeaderStyle(wb);
        XSSFCellStyle currencyStyle = createCurrencyStyle(wb);
        XSSFCellStyle dateStyle = createDateStyle(wb);
        XSSFCellStyle altRowStyle = createAltRowStyle(wb);

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

    // -------------------------------------------------------------------------
    // Sheet 2: By Payment Mode
    // -------------------------------------------------------------------------

    private void writeByPaymentModeSheet(XSSFWorkbook wb,
                                          Map<PaymentMode, List<Transaction>> grouped) {
        XSSFSheet sheet = wb.createSheet("By Payment Mode");

        XSSFCellStyle headerStyle = createHeaderStyle(wb);
        XSSFCellStyle currencyStyle = createCurrencyStyle(wb);
        XSSFCellStyle numberStyle = createNumberStyle(wb);

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
            debitCell.setCellValue(analyzer.totalDebit(entry.getValue()));
            debitCell.setCellStyle(currencyStyle);

            Cell creditCell = row.createCell(3);
            creditCell.setCellValue(analyzer.totalCredit(entry.getValue()));
            creditCell.setCellStyle(currencyStyle);
        }

        int dataLastRow = rowNum - 1;

        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 16 * 256);

        // Pie chart: payment mode labels (col 0) vs total debit (col 2)
        if (dataLastRow >= 1) {
            createPieChart(sheet, 0, 2, 1, dataLastRow,
                    5, 0, 13, 20, "Spend by Payment Mode");
        }
    }

    // -------------------------------------------------------------------------
    // Sheet 3: By Merchant
    // -------------------------------------------------------------------------

    private void writeByMerchantSheet(XSSFWorkbook wb,
                                       Map<String, List<Transaction>> grouped) {
        XSSFSheet sheet = wb.createSheet("By Merchant");

        XSSFCellStyle headerStyle = createHeaderStyle(wb);
        XSSFCellStyle currencyStyle = createCurrencyStyle(wb);
        XSSFCellStyle numberStyle = createNumberStyle(wb);

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
                debitCell.setCellValue(analyzer.totalDebit(entry.getValue()));
                debitCell.setCellStyle(currencyStyle);
            } else {
                otherTotal += analyzer.totalDebit(entry.getValue());
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
            createPieChart(sheet, 0, 2, 1, dataLastRow,
                    4, 0, 13, 22, "Top Merchants by Spend");
        }
    }

    // -------------------------------------------------------------------------
    // Sheet 4: By Month
    // -------------------------------------------------------------------------

    private void writeByMonthSheet(XSSFWorkbook wb,
                                    TreeMap<String, List<Transaction>> grouped) {
        XSSFSheet sheet = wb.createSheet("By Month");

        XSSFCellStyle headerStyle = createHeaderStyle(wb);
        XSSFCellStyle currencyStyle = createCurrencyStyle(wb);
        XSSFCellStyle numberStyle = createNumberStyle(wb);

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
            long debitCount  = txns.stream().filter(Transaction::isDebit).count();
            long creditCount = txns.stream().filter(Transaction::isCredit).count();

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());

            Cell dc = row.createCell(1);
            dc.setCellValue(debitCount);
            dc.setCellStyle(numberStyle);

            Cell td = row.createCell(2);
            td.setCellValue(analyzer.totalDebit(txns));
            td.setCellStyle(currencyStyle);

            Cell cc = row.createCell(3);
            cc.setCellValue(creditCount);
            cc.setCellStyle(numberStyle);

            Cell tc = row.createCell(4);
            tc.setCellValue(analyzer.totalCredit(txns));
            tc.setCellStyle(currencyStyle);
        }

        int dataLastRow = rowNum - 1;

        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        sheet.setColumnWidth(4, 16 * 256);

        if (dataLastRow >= 1) {
            createPieChart(sheet, 0, 2, 1, dataLastRow,
                    6, 0, 14, 22, "Monthly Spend Distribution");
        }
    }

    // -------------------------------------------------------------------------
    // Pie chart helper (XDDF API)
    // -------------------------------------------------------------------------

    private void createPieChart(XSSFSheet sheet,
                                  int labelCol, int valueCol,
                                  int dataFirstRow, int dataLastRow,
                                  int anchorCol1, int anchorRow1,
                                  int anchorCol2, int anchorRow2,
                                  String title) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(
                0, 0, 0, 0,
                anchorCol1, anchorRow1, anchorCol2, anchorRow2);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.RIGHT);

        XDDFCategoryDataSource labels = XDDFDataSourcesFactory.fromStringCellRange(
                sheet,
                new CellRangeAddress(dataFirstRow, dataLastRow, labelCol, labelCol));

        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet,
                new CellRangeAddress(dataFirstRow, dataLastRow, valueCol, valueCol));

        // PIE chart requires null for both axis parameters
        XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
        XDDFPieChartData pieData = (XDDFPieChartData) data;
        pieData.setVaryColors(true);

        XDDFChartData.Series series = pieData.addSeries(labels, values);
        series.setTitle(title, null);

        chart.plot(data);
    }

    // -------------------------------------------------------------------------
    // Cell style helpers
    // -------------------------------------------------------------------------

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 31, (byte) 73, (byte) 125}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle createCurrencyStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private XSSFCellStyle createNumberStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private XSSFCellStyle createDateStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("dd/mm/yyyy"));
        return style;
    }

    private XSSFCellStyle createLabelStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 219, (byte) 229, (byte) 241}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private XSSFCellStyle createAltRowStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 235, (byte) 241, (byte) 250}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private java.util.Date toJavaUtilDate(LocalDate ld) {
        return java.util.Date.from(
                ld.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
    }
}

package com.bankanalyzer.report.excel;

import com.bankanalyzer.api.dto.CustomerDetails;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Sheet 0 (optional): Customer & Account Information.
 */
@Component
@RequiredArgsConstructor
public class CustomerDetailsSheetWriter {

    private final ExcelStyleFactory styleFactory;

    public void write(XSSFWorkbook wb, CustomerDetails cd) {
        XSSFSheet sheet = wb.createSheet("Customer Details");
        XSSFCellStyle headerStyle = styleFactory.createHeaderStyle(wb);
        XSSFCellStyle labelStyle = styleFactory.createLabelStyle(wb);

        // Title row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Account & Customer Information");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        String[][] rows = {
                {"Customer Name", cd.getCustomerName()},
                {"Account Number", cd.getAccountNumber()},
                {"Product", cd.getProduct()},
                {"Account Status", cd.getAccountStatus()},
                {"Account Open Date", cd.getAccountOpenDate()},
                {"Branch", cd.getBranch()},
                {"Branch Code", cd.getBranchCode()},
                {"IFSC Code", cd.getIfscCode()},
                {"MICR Code", cd.getMicrCode()},
                {"CIF Number", cd.getCifNumber()},
                {"Email", cd.getEmail()},
                {"Mobile", cd.getMobile()},
                {"PAN", cd.getPan()},
                {"KYC Status", cd.getKycStatus()},
                {"Segment", cd.getSegment()},
                {"Currency", cd.getCurrency()},
                {"Closing Balance", cd.getClosingBalance()},
                {"Statement Period", cd.getStatementPeriod()},
                {"Statement Date", cd.getStatementDate()},
                {"Nominee", cd.getNomineeNam()},
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
}

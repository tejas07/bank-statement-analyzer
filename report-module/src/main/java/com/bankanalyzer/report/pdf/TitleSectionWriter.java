package com.bankanalyzer.report.pdf;

import com.bankanalyzer.api.dto.CustomerDetails;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.TransactionGroups;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static com.bankanalyzer.report.pdf.PdfStyleFactory.DARK_GRAY;
import static com.bankanalyzer.report.pdf.PdfStyleFactory.WHITE;
import static com.bankanalyzer.report.pdf.ReportFormatting.fmt;

/**
 * Title, transaction summary bar, and optional customer/account information table.
 */
@Component
@RequiredArgsConstructor
public class TitleSectionWriter {

    private final PdfStyleFactory styleFactory;

    public void write(Document doc, List<Transaction> txns, CustomerDetails cd) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, WHITE);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, DARK_GRAY);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, DARK_GRAY);

        Paragraph title = new Paragraph("Bank Statement Analysis Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8);
        doc.add(title);

        // ── Transaction summary bar ───────────────────────────────────────────
        LocalDate earliest = txns.stream().filter(t -> t.getDate() != null)
                .map(Transaction::getDate).min(Comparator.naturalOrder()).orElse(null);
        LocalDate latest = txns.stream().filter(t -> t.getDate() != null)
                .map(Transaction::getDate).max(Comparator.naturalOrder()).orElse(null);

        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setSpacingBefore(4);
        meta.setSpacingAfter(8);
        addMetaCell(meta, "Total Transactions", String.valueOf(txns.size()), labelFont, subFont);
        addMetaCell(meta, "Total Debit", fmt(TransactionGroups.totalDebit(txns)), labelFont, subFont);
        addMetaCell(meta, "Total Credit", fmt(TransactionGroups.totalCredit(txns)), labelFont, subFont);
        addMetaCell(meta, "Period",
                (earliest != null ? earliest + " to " + latest : "N/A"), labelFont, subFont);
        doc.add(meta);

        // ── Customer details table (if available) ─────────────────────────────
        if (cd != null) {
            doc.add(styleFactory.sectionHeader("Customer & Account Information"));
            PdfPTable cdTable = new PdfPTable(new float[]{25, 37, 25, 13});
            cdTable.setWidthPercentage(100);
            cdTable.setSpacingBefore(4);

            addCustomerRow(cdTable, labelFont, subFont,
                    "Customer Name", cd.getCustomerName(),
                    "Account Number", cd.getAccountNumber());
            addCustomerRow(cdTable, labelFont, subFont,
                    "Product", cd.getProduct(),
                    "Account Status", cd.getAccountStatus());
            addCustomerRow(cdTable, labelFont, subFont,
                    "Branch", cd.getBranch(),
                    "Branch Code", cd.getBranchCode());
            addCustomerRow(cdTable, labelFont, subFont,
                    "IFSC Code", cd.getIfscCode(),
                    "MICR Code", cd.getMicrCode());
            addCustomerRow(cdTable, labelFont, subFont,
                    "CIF Number", cd.getCifNumber(),
                    "Account Open Date", cd.getAccountOpenDate());
            addCustomerRow(cdTable, labelFont, subFont,
                    "Email", cd.getEmail(),
                    "Mobile", cd.getMobile());
            addCustomerRow(cdTable, labelFont, subFont,
                    "PAN", cd.getPan(),
                    "KYC Status", cd.getKycStatus());
            addCustomerRow(cdTable, labelFont, subFont,
                    "Segment", cd.getSegment(),
                    "Currency", cd.getCurrency());
            addCustomerRow(cdTable, labelFont, subFont,
                    "Closing Balance", cd.getClosingBalance(),
                    "Statement Period", cd.getStatementPeriod());
            addCustomerRow(cdTable, labelFont, subFont,
                    "Statement Date", cd.getStatementDate(),
                    "Nominee", cd.getNomineeNam());
            doc.add(cdTable);
        }
    }

    private void addMetaCell(PdfPTable table, String label, String value,
                             Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8);
        cell.setBorderColor(new Color(200, 200, 200));
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valueFont));
        table.addCell(cell);
    }

    private void addCustomerRow(PdfPTable table, Font labelFont, Font valueFont,
                                String label1, String value1,
                                String label2, String value2) {
        if ((value1 == null || value1.isBlank()) && (value2 == null || value2.isBlank())) return;
        Font lf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, DARK_GRAY);
        Font vf = FontFactory.getFont(FontFactory.HELVETICA, 8, DARK_GRAY);

        addInfoCell(table, label1 != null ? label1 : "", lf);
        addInfoCell(table, value1 != null ? value1 : "", vf);
        addInfoCell(table, label2 != null ? label2 : "", lf);
        addInfoCell(table, value2 != null ? value2 : "", vf);
    }

    private void addInfoCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new com.lowagie.text.Phrase(text, font));
        cell.setPadding(4);
        cell.setBorderColor(new Color(210, 210, 210));
        table.addCell(cell);
    }
}

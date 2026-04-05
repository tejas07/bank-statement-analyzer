package com.bankanalyzer.report;

import com.bankanalyzer.analyzer.TransactionAnalyzer;
import com.bankanalyzer.api.dto.CustomerDetails;
import com.bankanalyzer.api.dto.DuplicateGroup;
import com.bankanalyzer.model.Category;
import com.bankanalyzer.model.PaymentMode;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.service.DuplicateDetector;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Feature 5: Generates a multi-section PDF report using OpenPDF.
 *
 * Sections:
 *   1. Summary (bank, dates, totals)
 *   2. All Transactions table
 *   3. Spend by Category
 *   4. Spend by Payment Mode
 *   5. Monthly Breakdown
 *   6. Duplicate Transactions (if any)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfReportGenerator {

    private static final Color HEADER_BG  = new Color(31, 73, 125);
    private static final Color ALT_ROW_BG = new Color(235, 241, 250);
    private static final Color WHITE      = Color.WHITE;
    private static final Color DARK_GRAY  = new Color(50, 50, 50);

    private final TransactionAnalyzer analyzer;
    private final DuplicateDetector   duplicateDetector;

    public byte[] generateBytes(List<Transaction> transactions) throws IOException {
        return generateBytes(transactions, null);
    }

    public byte[] generateBytes(List<Transaction> transactions,
                                 CustomerDetails customerDetails) throws IOException {
        log.info("Generating PDF report with {} transactions", transactions.size());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 54, 36);
        PdfWriter writer = PdfWriter.getInstance(doc, bos);
        writer.setPageEvent(new HeaderFooterPageEvent());
        doc.open();

        addTitleSection(doc, transactions, customerDetails);
        doc.add(Chunk.NEWLINE);

        addTransactionsTable(doc, transactions);
        doc.newPage();

        addCategorySection(doc, transactions);
        doc.newPage();

        addPaymentModeSection(doc, transactions);
        doc.add(Chunk.NEWLINE);
        addMonthlySection(doc, transactions);

        List<DuplicateGroup> duplicates = duplicateDetector.detect(transactions);
        if (!duplicates.isEmpty()) {
            doc.newPage();
            addDuplicatesSection(doc, duplicates);
        }

        doc.close();
        return bos.toByteArray();
    }

    // ── Title / Summary section ───────────────────────────────────────────────

    private void addTitleSection(Document doc, List<Transaction> txns,
                                  CustomerDetails cd) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, WHITE);
        Font subFont   = FontFactory.getFont(FontFactory.HELVETICA, 10, DARK_GRAY);
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
        addMetaCell(meta, "Total Debit",  fmt(analyzer.totalDebit(txns)),  labelFont, subFont);
        addMetaCell(meta, "Total Credit", fmt(analyzer.totalCredit(txns)), labelFont, subFont);
        addMetaCell(meta, "Period",
            (earliest != null ? earliest + " to " + latest : "N/A"), labelFont, subFont);
        doc.add(meta);

        // ── Customer details table (if available) ─────────────────────────────
        if (cd != null) {
            doc.add(sectionHeader("Customer & Account Information"));
            PdfPTable cdTable = new PdfPTable(new float[]{25, 37, 25, 13});
            cdTable.setWidthPercentage(100);
            cdTable.setSpacingBefore(4);

            addCustomerRow(cdTable, labelFont, subFont,
                "Customer Name",    cd.getCustomerName(),
                "Account Number",   cd.getAccountNumber());
            addCustomerRow(cdTable, labelFont, subFont,
                "Product",          cd.getProduct(),
                "Account Status",   cd.getAccountStatus());
            addCustomerRow(cdTable, labelFont, subFont,
                "Branch",           cd.getBranch(),
                "Branch Code",      cd.getBranchCode());
            addCustomerRow(cdTable, labelFont, subFont,
                "IFSC Code",        cd.getIfscCode(),
                "MICR Code",        cd.getMicrCode());
            addCustomerRow(cdTable, labelFont, subFont,
                "CIF Number",       cd.getCifNumber(),
                "Account Open Date",cd.getAccountOpenDate());
            addCustomerRow(cdTable, labelFont, subFont,
                "Email",            cd.getEmail(),
                "Mobile",           cd.getMobile());
            addCustomerRow(cdTable, labelFont, subFont,
                "PAN",              cd.getPan(),
                "KYC Status",       cd.getKycStatus());
            addCustomerRow(cdTable, labelFont, subFont,
                "Segment",          cd.getSegment(),
                "Currency",         cd.getCurrency());
            addCustomerRow(cdTable, labelFont, subFont,
                "Closing Balance",  cd.getClosingBalance(),
                "Statement Period", cd.getStatementPeriod());
            addCustomerRow(cdTable, labelFont, subFont,
                "Statement Date",   cd.getStatementDate(),
                "Nominee",          cd.getNomineeNam());
            doc.add(cdTable);
        }
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
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setBorderColor(new Color(210, 210, 210));
        table.addCell(cell);
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

    // ── All Transactions table ────────────────────────────────────────────────

    private void addTransactionsTable(Document doc, List<Transaction> txns)
            throws DocumentException {
        doc.add(sectionHeader("All Transactions"));

        PdfPTable table = new PdfPTable(new float[]{12, 32, 14, 20, 11, 11});
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setHeaderRows(1);

        addTableHeader(table, "Date", "Description", "Mode", "Merchant", "Debit", "Credit");

        int rowIdx = 0;
        for (Transaction t : txns) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            addRow(table, bg,
                t.getDate() != null ? t.getDate().toString() : "",
                truncate(t.getDescription(), 60),
                t.getPaymentMode().getLabel(),
                truncate(t.getMerchantName(), 28),
                t.getDebit() > 0 ? fmt(t.getDebit()) : "",
                t.getCredit() > 0 ? fmt(t.getCredit()) : "");
        }
        doc.add(table);
    }

    // ── Category breakdown ────────────────────────────────────────────────────

    private void addCategorySection(Document doc, List<Transaction> txns)
            throws DocumentException {
        doc.add(sectionHeader("Spend by Category"));

        Map<Category, Double> byCategory = txns.stream()
            .filter(Transaction::isDebit)
            .collect(Collectors.groupingBy(Transaction::getCategory,
                Collectors.summingDouble(Transaction::getDebit)));

        List<Map.Entry<Category, Double>> sorted = byCategory.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());

        PdfPTable table = new PdfPTable(new float[]{40, 20, 40});
        table.setWidthPercentage(70);
        table.setSpacingBefore(6);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);
        addTableHeader(table, "Category", "Txn Count", "Total Debit");

        int rowIdx = 0;
        for (Map.Entry<Category, Double> entry : sorted) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            long count = txns.stream().filter(t -> t.isDebit() && t.getCategory() == entry.getKey()).count();
            addRow(table, bg,
                entry.getKey().name().replace('_', ' '),
                String.valueOf(count),
                fmt(entry.getValue()));
        }
        doc.add(table);
    }

    // ── Payment mode breakdown ────────────────────────────────────────────────

    private void addPaymentModeSection(Document doc, List<Transaction> txns)
            throws DocumentException {
        doc.add(sectionHeader("Spend by Payment Mode"));

        Map<PaymentMode, List<Transaction>> byMode = analyzer.groupByPaymentMode(txns);

        PdfPTable table = new PdfPTable(new float[]{25, 15, 25, 25});
        table.setWidthPercentage(70);
        table.setSpacingBefore(6);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);
        addTableHeader(table, "Payment Mode", "Count", "Total Debit", "Total Credit");

        int rowIdx = 0;
        for (Map.Entry<PaymentMode, List<Transaction>> e : byMode.entrySet()) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            addRow(table, bg,
                e.getKey().getLabel(),
                String.valueOf(e.getValue().size()),
                fmt(analyzer.totalDebit(e.getValue())),
                fmt(analyzer.totalCredit(e.getValue())));
        }
        doc.add(table);
    }

    // ── Monthly breakdown ─────────────────────────────────────────────────────

    private void addMonthlySection(Document doc, List<Transaction> txns)
            throws DocumentException {
        doc.add(sectionHeader("Monthly Breakdown"));

        TreeMap<String, List<Transaction>> byMonth = analyzer.groupByMonth(txns);

        PdfPTable table = new PdfPTable(new float[]{18, 15, 22, 15, 22});
        table.setWidthPercentage(80);
        table.setSpacingBefore(6);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setHeaderRows(1);
        addTableHeader(table, "Month", "Debit Count", "Total Debit", "Credit Count", "Total Credit");

        int rowIdx = 0;
        for (Map.Entry<String, List<Transaction>> e : byMonth.entrySet()) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            List<Transaction> m = e.getValue();
            addRow(table, bg,
                e.getKey(),
                String.valueOf(m.stream().filter(Transaction::isDebit).count()),
                fmt(analyzer.totalDebit(m)),
                String.valueOf(m.stream().filter(Transaction::isCredit).count()),
                fmt(analyzer.totalCredit(m)));
        }
        doc.add(table);
    }

    // ── Duplicate transactions section ────────────────────────────────────────

    private void addDuplicatesSection(Document doc, List<DuplicateGroup> duplicates)
            throws DocumentException {
        doc.add(sectionHeader("Possible Duplicate Transactions (" + duplicates.size() + " groups)"));

        Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, new Color(120, 0, 0));
        Paragraph note = new Paragraph(
            "The following transactions appear more than once with the same description and amount.", noteFont);
        note.setSpacingBefore(4);
        note.setSpacingAfter(6);
        doc.add(note);

        PdfPTable table = new PdfPTable(new float[]{40, 15, 15, 10, 20});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setHeaderRows(1);
        addTableHeader(table, "Description", "Debit", "Credit", "Count", "Dates");

        int rowIdx = 0;
        for (DuplicateGroup g : duplicates) {
            Color bg = (rowIdx++ % 2 == 1) ? ALT_ROW_BG : WHITE;
            addRow(table, bg,
                truncate(g.getDescription(), 55),
                g.getDebit() > 0 ? fmt(g.getDebit()) : "",
                g.getCredit() > 0 ? fmt(g.getCredit()) : "",
                String.valueOf(g.getCount()),
                String.join(", ", g.getOccurrenceDates()));
        }
        doc.add(table);
    }

    // ── Table helpers ─────────────────────────────────────────────────────────

    private void addTableHeader(PdfPTable table, String... headers) {
        Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addRow(PdfPTable table, Color bg, String... values) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 8, DARK_GRAY);
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v, font));
            cell.setBackgroundColor(bg);
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private Paragraph sectionHeader(String title) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, HEADER_BG);
        Paragraph p = new Paragraph(title, f);
        p.setSpacingBefore(14);
        p.setSpacingAfter(2);
        return p;
    }

    private String fmt(double v) {
        return String.format("%,.2f", v);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    // ── Page header / footer event ────────────────────────────────────────────

    private static class HeaderFooterPageEvent extends PdfPageEventHelper {
        private static final Font FOOTER_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(120, 120, 120));

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String text = "Bank Statement Analyzer  |  Page " + writer.getPageNumber();
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase(text, FOOTER_FONT),
                (document.left() + document.right()) / 2,
                document.bottom() - 18, 0);
        }
    }
}

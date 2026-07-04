package com.bankanalyzer.report.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;

/**
 * Footer with page number, applied to every page of the PDF report.
 */
public class ReportHeaderFooterEvent extends PdfPageEventHelper {

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

package com.bankanalyzer.report.pdf;

/**
 * Small string-formatting helpers shared by two or more PDF section writers.
 */
public final class ReportFormatting {

    private ReportFormatting() {
    }

    public static String fmt(double v) {
        return String.format("%,.2f", v);
    }

    public static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}

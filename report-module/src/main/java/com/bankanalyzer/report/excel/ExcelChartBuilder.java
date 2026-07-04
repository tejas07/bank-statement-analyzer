package com.bankanalyzer.report.excel;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.springframework.stereotype.Component;

/**
 * Isolates all XDDF pie-chart API usage for the XLSX sheet writers.
 */
@Component
public class ExcelChartBuilder {

    public void createPieChart(XSSFSheet sheet,
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
}

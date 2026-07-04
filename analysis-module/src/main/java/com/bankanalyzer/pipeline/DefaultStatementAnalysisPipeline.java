package com.bankanalyzer.pipeline;

import com.bankanalyzer.analyzer.TransactionAnalysis;
import com.bankanalyzer.api.dto.CustomerDetails;
import com.bankanalyzer.api.dto.CustomerDetailsMapper;
import com.bankanalyzer.api.dto.SummaryResponse;
import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.parser.StatementParsing;
import com.bankanalyzer.report.ExcelReportGenerator;
import com.bankanalyzer.report.PdfReportGenerator;
import com.bankanalyzer.service.PersistenceGateway;
import com.bankanalyzer.service.SummaryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultStatementAnalysisPipeline implements StatementAnalysisPipeline {

    private final StatementParsing parser;
    private final TransactionAnalysis analyzer;
    private final SummaryBuilder summaryBuilder;
    private final PersistenceGateway persistenceGateway;
    private final ExcelReportGenerator excelReportGenerator;
    private final PdfReportGenerator pdfReportGenerator;

    @Override
    @Cacheable(cacheNames = "analysis", key = "#hash + ':summary'")
    public SummaryResponse buildSummaryCached(String hash, byte[] fileBytes, String originalFilename) throws IOException {
        return analyzeAndPersist(fileBytes, originalFilename);
    }

    @Override
    public SummaryResponse analyzeAndPersist(byte[] fileBytes, String originalFilename) throws IOException {
        ParsedStatement ps = parseAnalyzeAndPersist(fileBytes, originalFilename);
        SummaryResponse summary = summaryBuilder.build(ps.enriched(), ps.parsed());
        return summary.toBuilder().uploadId(ps.uploadId()).build();
    }

    private ParsedStatement parseAnalyzeAndPersist(byte[] fileBytes, String originalFilename) throws IOException {
        ParseResult parsed = parser.parseWithMeta(new ByteArrayInputStream(fileBytes));
        List<Transaction> enriched = analyzer.analyze(parsed.getTransactions());
        String hash = DigestUtils.md5DigestAsHex(fileBytes);
        Long uploadId = persistenceGateway.save(hash, originalFilename, parsed, enriched);
        return new ParsedStatement(parsed, enriched, uploadId);
    }

    @Override
    @Cacheable(cacheNames = "analysis", key = "#hash + ':report'")
    public byte[] buildExcelReportCached(String hash, byte[] fileBytes, String originalFilename) throws IOException {
        ParsedStatement ps = parseAnalyzeAndPersist(fileBytes, originalFilename);
        CustomerDetails customerDetails = CustomerDetailsMapper.toDto(ps.parsed().getCustomerDetails());
        return excelReportGenerator.generateBytes(ps.enriched(), customerDetails);
    }

    @Override
    @Cacheable(cacheNames = "analysis", key = "#hash + ':pdf'")
    public byte[] buildPdfReportCached(String hash, byte[] fileBytes, String originalFilename) throws IOException {
        ParsedStatement ps = parseAnalyzeAndPersist(fileBytes, originalFilename);
        CustomerDetails customerDetails = CustomerDetailsMapper.toDto(ps.parsed().getCustomerDetails());
        return pdfReportGenerator.generateBytes(ps.enriched(), customerDetails);
    }
}

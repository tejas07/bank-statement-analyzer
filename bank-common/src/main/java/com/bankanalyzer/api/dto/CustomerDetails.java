package com.bankanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Customer and account metadata extracted from the PDF statement header.
 * All fields are optional — only populated when found in the PDF text.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerDetails {
    private final String customerName;
    private final String accountNumber;
    private final String product;          // e.g. Savings Account, Credit Card
    private final String branch;
    private final String branchCode;
    private final String ifscCode;
    private final String micrCode;
    private final String cifNumber;
    private final String email;
    private final String mobile;
    private final String pan;
    private final String kycStatus;
    private final String segment;          // e.g. Gold, Silver
    private final String accountStatus;   // e.g. OPEN
    private final String accountOpenDate;
    private final String statementPeriod; // e.g. 01-01-2022 to 04-04-2026
    private final String statementDate;
    private final String closingBalance;
    private final String currency;
    private final String nomineeNam;
}

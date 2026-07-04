package com.bankanalyzer.api.dto;

/**
 * Maps the domain {@link com.bankanalyzer.model.CustomerDetails} (produced by the
 * parser layer) to the API-facing {@link CustomerDetails} DTO. Keeps the mapping at
 * the API boundary so {@code api.dto} stays the only layer that knows about DTOs.
 */
public final class CustomerDetailsMapper {

    private CustomerDetailsMapper() {
    }

    public static CustomerDetails toDto(com.bankanalyzer.model.CustomerDetails domain) {
        if (domain == null) return null;
        return CustomerDetails.builder()
                .customerName(domain.getCustomerName())
                .accountNumber(domain.getAccountNumber())
                .product(domain.getProduct())
                .branch(domain.getBranch())
                .branchCode(domain.getBranchCode())
                .ifscCode(domain.getIfscCode())
                .micrCode(domain.getMicrCode())
                .cifNumber(domain.getCifNumber())
                .email(domain.getEmail())
                .mobile(domain.getMobile())
                .pan(domain.getPan())
                .kycStatus(domain.getKycStatus())
                .segment(domain.getSegment())
                .accountStatus(domain.getAccountStatus())
                .accountOpenDate(domain.getAccountOpenDate())
                .statementPeriod(domain.getStatementPeriod())
                .statementDate(domain.getStatementDate())
                .closingBalance(domain.getClosingBalance())
                .currency(domain.getCurrency())
                .nomineeNam(domain.getNomineeNam())
                .build();
    }
}

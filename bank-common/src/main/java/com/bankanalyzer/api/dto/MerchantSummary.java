package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MerchantSummary {
    private final String merchant;
    private final int count;
    private final double totalDebit;
}

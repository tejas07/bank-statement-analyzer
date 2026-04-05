package com.bankanalyzer.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DuplicateGroup {
    private final String       description;
    private final double       debit;
    private final double       credit;
    private final int          count;
    private final List<String> occurrenceDates;
}

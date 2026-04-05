package com.bankanalyzer.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the correct BankParser for a given PDF text.
 *
 * Spring injects all BankParser beans ordered by @Order value.
 * The first parser whose supports() returns true is used.
 * GenericBankParser (lowest precedence) acts as the fallback.
 */
@Slf4j
@Component
public class BankParserRegistry {

    private final List<BankParser> parsers;

    public BankParserRegistry(List<BankParser> parsers) {
        this.parsers = parsers;
        parsers.forEach(p -> log.info("Registered parser: {}", p.bankName()));
    }

    public BankParser resolve(String rawText) {
        return parsers.stream()
            .filter(p -> p.supports(rawText))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No parser matched — GenericBankParser should always be the fallback"));
    }
}

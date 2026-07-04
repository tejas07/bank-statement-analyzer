package com.bankanalyzer.service;

import com.bankanalyzer.api.dto.DuplicateGroup;
import com.bankanalyzer.model.DuplicateTransactionFinder;
import com.bankanalyzer.model.Transaction;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Feature 3: Detects duplicate transactions within a statement.
 * Thin Spring-managed delegate over the framework-free
 * {@link DuplicateTransactionFinder} (bank-common), so DI-based callers keep
 * injecting a bean while report-module can call the algorithm directly.
 */
@Service
public class DuplicateDetector {

    public List<DuplicateGroup> detect(List<Transaction> transactions) {
        return DuplicateTransactionFinder.find(transactions);
    }
}

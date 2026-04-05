package com.bankanalyzer.service;

import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.entity.StatementUploadEntity;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over persistence operations.
 * Two implementations:
 *   - NoOpPersistenceGateway  — active when persistence.enabled=false (default)
 *   - StatementPersistenceService — active when persistence.enabled=true
 */
public interface PersistenceGateway {

    /**
     * Returns an existing upload if the same file was uploaded within the dedup window.
     * Returns empty when persistence is disabled.
     */
    Optional<StatementUploadEntity> findDuplicate(String fileHash);

    /**
     * Persists upload metadata and all enriched transactions.
     * Returns the generated upload ID, or null when persistence is disabled.
     */
    Long save(String fileHash, String originalFilename,
              ParseResult parseResult, List<Transaction> enriched);
}

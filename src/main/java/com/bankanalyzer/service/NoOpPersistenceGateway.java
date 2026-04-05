package com.bankanalyzer.service;

import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.entity.StatementUploadEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * No-op implementation of PersistenceGateway.
 * Active by default (persistence.enabled=false).
 * No database connection is required or attempted.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "persistence.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpPersistenceGateway implements PersistenceGateway {

    @Override
    public Optional<StatementUploadEntity> findDuplicate(String fileHash) {
        return Optional.empty();
    }

    @Override
    public Long save(String fileHash, String originalFilename,
                     ParseResult parseResult, List<Transaction> enriched) {
        log.debug("Persistence disabled — skipping save for file hash {}", fileHash);
        return null;
    }
}

package com.bankanalyzer.repository;

import com.bankanalyzer.model.entity.StatementUploadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StatementUploadRepository extends JpaRepository<StatementUploadEntity, Long> {

    /**
     * Finds an upload with the same file hash uploaded after the given cutoff time.
     * Used for duplicate detection within the configured dedup window.
     */
    Optional<StatementUploadEntity> findFirstByFileHashAndUploadedAtAfterOrderByUploadedAtDesc(
        String fileHash, LocalDateTime cutoff);
}

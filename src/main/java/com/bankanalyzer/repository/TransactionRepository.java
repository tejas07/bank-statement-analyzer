package com.bankanalyzer.repository;

import com.bankanalyzer.model.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    List<TransactionEntity> findByUploadId(Long uploadId);
}

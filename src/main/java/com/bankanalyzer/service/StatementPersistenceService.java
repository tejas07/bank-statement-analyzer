package com.bankanalyzer.service;

import com.bankanalyzer.config.DedupProperties;
import com.bankanalyzer.model.ParseResult;
import com.bankanalyzer.model.Transaction;
import com.bankanalyzer.model.entity.StatementUploadEntity;
import com.bankanalyzer.model.entity.TransactionEntity;
import com.bankanalyzer.repository.StatementUploadRepository;
import com.bankanalyzer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Active only when persistence.enabled=true.
 * Saves every upload and its transactions to PostgreSQL.
 * Also performs duplicate-detection within the configured dedup window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "persistence.enabled", havingValue = "true")
public class StatementPersistenceService implements PersistenceGateway {

    private final StatementUploadRepository uploadRepo;
    private final TransactionRepository     txnRepo;
    private final DedupProperties           dedupProps;

    @Override
    public Optional<StatementUploadEntity> findDuplicate(String fileHash) {
        if (!dedupProps.isEnabled()) return Optional.empty();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(dedupProps.getWindowHours());
        return uploadRepo.findFirstByFileHashAndUploadedAtAfterOrderByUploadedAtDesc(fileHash, cutoff);
    }

    @Override
    @Transactional
    public Long save(String fileHash, String originalFilename,
                     ParseResult parseResult, List<Transaction> enriched) {

        StatementUploadEntity upload = StatementUploadEntity.builder()
            .fileHash(fileHash)
            .originalFilename(originalFilename)
            .bankName(parseResult.getBankName())
            .statementType(parseResult.getStatementType().name())
            .transactionCount(enriched.size())
            .uploadedAt(LocalDateTime.now())
            .build();

        final StatementUploadEntity savedUpload = uploadRepo.save(upload);

        List<TransactionEntity> entities = enriched.stream()
            .map(t -> TransactionEntity.builder()
                .upload(savedUpload)
                .txnDate(t.getDate())
                .description(t.getDescription())
                .debit(t.getDebit())
                .credit(t.getCredit())
                .balance(t.getBalance())
                .paymentMode(t.getPaymentMode().name())
                .merchantName(t.getMerchantName())
                .category(t.getCategory().name())
                .build())
            .collect(Collectors.toList());

        txnRepo.saveAll(entities);
        log.info("Persisted upload {} with {} transactions", savedUpload.getId(), entities.size());
        return savedUpload.getId();
    }
}

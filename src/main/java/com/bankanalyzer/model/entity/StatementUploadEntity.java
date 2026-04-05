package com.bankanalyzer.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "statement_uploads", indexes = {
    @Index(name = "idx_uploads_hash", columnList = "file_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementUploadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_hash", nullable = false, length = 32)
    private String fileHash;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "statement_type", length = 50)
    private String statementType;

    @Column(name = "transaction_count")
    private int transactionCount;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}

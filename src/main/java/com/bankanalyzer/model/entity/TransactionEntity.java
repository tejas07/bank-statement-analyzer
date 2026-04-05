package com.bankanalyzer.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_txn_upload",   columnList = "upload_id"),
    @Index(name = "idx_txn_date",     columnList = "txn_date"),
    @Index(name = "idx_txn_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id", nullable = false)
    private StatementUploadEntity upload;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private double debit;

    @Column(nullable = false)
    private double credit;

    @Column(nullable = false)
    private double balance;

    @Column(name = "payment_mode", length = 50)
    private String paymentMode;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(length = 50)
    private String category;
}

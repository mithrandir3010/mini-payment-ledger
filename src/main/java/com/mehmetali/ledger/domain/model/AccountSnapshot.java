package com.mehmetali.ledger.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_snapshots")
public class AccountSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /**
     * Bu timestamp'e kadar olan tüm ledger_entries bu bakiyeye dahildir.
     * Hibrit sorguda: created_at > snapshotted_at şartıyla delta toplanır.
     */
    @Column(name = "snapshotted_at", nullable = false)
    private LocalDateTime snapshottedAt;

    @Column(name = "entry_count", nullable = false)
    private Long entryCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

package com.mehmetali.ledger.domain.repository;

import com.mehmetali.ledger.domain.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
            FROM LedgerEntry e
            WHERE e.accountId = :accountId
            """)
    BigDecimal calculateBalance(@Param("accountId") UUID accountId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
            FROM LedgerEntry e
            WHERE e.accountId = :accountId AND e.createdAt > :after
            """)
    BigDecimal calculateBalanceAfter(@Param("accountId") UUID accountId,
                                     @Param("after") LocalDateTime after);

    long countByAccountId(UUID accountId);

    long countByAccountIdAndCreatedAtAfter(UUID accountId, LocalDateTime after);

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}

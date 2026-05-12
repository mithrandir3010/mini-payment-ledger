package com.mehmetali.ledger.domain.repository;

import com.mehmetali.ledger.domain.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}

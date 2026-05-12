package com.mehmetali.ledger.domain.repository;

import com.mehmetali.ledger.domain.model.AccountSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshot, Long> {

    Optional<AccountSnapshot> findTopByAccountIdOrderBySnapshottedAtDesc(UUID accountId);
}

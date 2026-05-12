package com.mehmetali.ledger.domain.repository;

import com.mehmetali.ledger.domain.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}

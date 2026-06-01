package com.mehmetali.ledger.domain.repository;

import com.mehmetali.ledger.domain.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);
}

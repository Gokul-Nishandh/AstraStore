/**
 * JPA repository for ApiKey entity — manages persistent API key records.
 * Supports lookup by user, key hash for authentication, and ownership checks.
 * Used by ApiKeyService for CRUD and validation operations.
 */
package com.astrastore.auth.repository;

import com.astrastore.auth.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByUserIdAndRevokedFalse(Long userId);

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByIdAndUserId(Long id, Long userId);
}

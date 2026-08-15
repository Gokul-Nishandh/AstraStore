/**
 * JPA repository for ApiKey entity — manages persistent API key records.
 * Supports lookup by user, key hash for authentication, and ownership checks.
 * Used by ApiKeyService for CRUD and validation operations.
 */
package com.astrastore.auth.repository;

import com.astrastore.auth.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByUserIdAndRevokedFalse(Long userId);

    List<ApiKey> findByKeyPrefixAndRevokedFalse(String keyPrefix);

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByIdAndUserId(Long id, Long userId);

    /**
     * Live key counts for a page of users, in one query rather than one per
     * row. Returns {@code [userId, count]} pairs; users with no live key are
     * simply absent.
     */
    @Query("SELECT k.userId, COUNT(k) FROM ApiKey k WHERE k.userId IN :userIds AND k.revoked = false GROUP BY k.userId")
    List<Object[]> countActiveByUserIds(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query("DELETE FROM ApiKey k WHERE k.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}

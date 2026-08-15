/**
 * JPA repository for RefreshToken entity — manages persistent refresh token records.
 * Supports token hash lookup, bulk revocation by user, and expired-token cleanup.
 * Used by RefreshTokenService alongside Redis caching layer.
 */
package com.astrastore.auth.repository;

import com.astrastore.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId")
    int revokeAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") java.time.Instant now);

    /**
     * Hard-deletes every refresh token belonging to a user. Used when the
     * account itself goes away — a revoked-but-present row would keep a
     * dangling reference to an id that no longer resolves.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}

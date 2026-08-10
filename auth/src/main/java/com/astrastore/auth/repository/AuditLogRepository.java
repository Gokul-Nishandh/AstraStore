/**
 * JPA repository for AuditLog entity — handles audit event persistence and retrieval.
 * Supports paginated queries for user/all logs and bulk deletion for cold-storage archiving.
 * Backed by PostgreSQL with indexes on user_id, action, and timestamp.
 */
package com.astrastore.auth.repository;

import com.astrastore.auth.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

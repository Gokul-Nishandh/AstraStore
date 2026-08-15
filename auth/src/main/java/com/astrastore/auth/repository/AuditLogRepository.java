/**
 * JPA repository for AuditLog entity — handles audit event persistence and retrieval.
 * Supports paginated and filtered queries plus bulk deletion for cold-storage archiving.
 * Backed by PostgreSQL with indexes on (user_id, timestamp), timestamp and action.
 */
package com.astrastore.auth.repository;

import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * The action values actually present in the table. The filter dropdown is
     * built from this rather than from the enum, so it never offers a value
     * that would return nothing.
     */
    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<AuditAction> findDistinctActions();

    @Query("SELECT DISTINCT a.action FROM AuditLog a WHERE a.userId = :userId ORDER BY a.action")
    List<AuditAction> findDistinctActionsForUser(@Param("userId") Long userId);

    /**
     * Severs a deleted account from its audit trail without destroying the
     * trail. The security history of the deployment survives; the identity
     * attached to it does not.
     */
    @Modifying
    @Query("UPDATE AuditLog a SET a.userId = null, a.actorEmail = :marker WHERE a.userId = :userId")
    int anonymiseForUser(@Param("userId") Long userId, @Param("marker") String marker);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

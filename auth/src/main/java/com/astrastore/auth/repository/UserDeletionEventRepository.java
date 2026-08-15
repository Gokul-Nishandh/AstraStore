/**
 * JPA repository for the cross-service account-deletion outbox.
 * Other services poll {@code findByProcessedFalseOrderByRequestedAtAsc} —
 * directly against the shared database, or over the admin HTTP endpoint.
 */
package com.astrastore.auth.repository;

import com.astrastore.auth.entity.UserDeletionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDeletionEventRepository extends JpaRepository<UserDeletionEvent, Long> {

    Page<UserDeletionEvent> findByProcessedFalseOrderByRequestedAtAsc(Pageable pageable);

    Page<UserDeletionEvent> findAllByOrderByRequestedAtDesc(Pageable pageable);
}

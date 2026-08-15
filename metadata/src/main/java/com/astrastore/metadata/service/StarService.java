package com.astrastore.metadata.service;

import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStar;
import com.astrastore.metadata.repository.ObjectStarRepository;
import com.astrastore.shared.security.AstraPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user stars.
 *
 * <p>Both mutations are idempotent, because the UI fires them from a toggle
 * that can double-fire and from more than one tab at a time: starring twice is
 * one star, unstarring something that was never starred is a success.
 *
 * <p>Starring is gated on the same ownership rule as reading — you can only
 * star what you can see — so a caller cannot use the star table to probe for
 * another account's object ids.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StarService {

    private final ObjectStarRepository objectStarRepository;
    private final ObjectService objectService;

    /**
     * Stars an object the caller owns.
     *
     * @return {@code true} if this call created the star, {@code false} if it
     *         was already there
     */
    public boolean star(UUID objectId, AstraPrincipal principal) {
        // Ownership first: a non-owner must get NOT_FOUND, not a star.
        ObjectRecord record = objectService.getObjectForUser(objectId, principal);
        Long userId = principal.userId();

        if (objectStarRepository.existsByUserIdAndObjectId(userId, record.getId())) {
            return false;
        }
        try {
            objectStarRepository.save(ObjectStar.builder()
                    .userId(userId)
                    .objectId(record.getId())
                    .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            // Two concurrent stars raced past the exists() check; the unique
            // constraint settled it and the caller's intent is satisfied.
            log.debug("Concurrent star on object {} by user {} — treated as already starred.", objectId, userId);
            return false;
        }
    }

    /**
     * Removes the caller's star.
     *
     * @return {@code true} if a star was removed
     */
    public boolean unstar(UUID objectId, AstraPrincipal principal) {
        ObjectRecord record = objectService.getAnyObjectForUser(objectId, principal);
        return objectStarRepository.deleteStar(principal.userId(), record.getId()) > 0;
    }

    @Transactional(readOnly = true)
    public boolean isStarred(UUID objectId, Long userId) {
        return objectStarRepository.existsByUserIdAndObjectId(userId, objectId);
    }

    @Transactional(readOnly = true)
    public long countForUser(Long userId) {
        return objectStarRepository.countByUserId(userId);
    }

    /**
     * Resolves the starred flag for a page of objects in a single query.
     *
     * @return the subset of {@code objectIds} the user has starred
     */
    @Transactional(readOnly = true)
    public Set<UUID> starredAmong(Long userId, Collection<UUID> objectIds) {
        if (userId == null || objectIds == null || objectIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> starred = objectStarRepository.findStarredObjectIds(userId, objectIds);
        return Set.copyOf(starred);
    }
}

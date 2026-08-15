package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.ObjectStar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObjectStarRepository extends JpaRepository<ObjectStar, UUID> {

    Optional<ObjectStar> findByUserIdAndObjectId(Long userId, UUID objectId);

    boolean existsByUserIdAndObjectId(Long userId, UUID objectId);

    long countByUserId(Long userId);

    @Modifying
    @Query("delete from ObjectStar s where s.userId = :userId and s.objectId = :objectId")
    int deleteStar(@Param("userId") Long userId, @Param("objectId") UUID objectId);

    /**
     * Removes every user's star on an object. Called when the object is hard
     * deleted, so a star never outlives the row it points at.
     */
    @Modifying
    @Query("delete from ObjectStar s where s.objectId in :objectIds")
    int deleteByObjectIdIn(@Param("objectIds") Collection<UUID> objectIds);

    /**
     * Resolves the {@code starred} flag for a whole page in one query.
     *
     * <p>Deliberately not a per-row {@code exists} call: a 50-row listing would
     * otherwise cost 50 round trips, and listings are the hottest endpoint in
     * the service.
     */
    @Query("select s.objectId from ObjectStar s where s.userId = :userId and s.objectId in :objectIds")
    List<UUID> findStarredObjectIds(@Param("userId") Long userId,
                                    @Param("objectIds") Collection<UUID> objectIds);
}

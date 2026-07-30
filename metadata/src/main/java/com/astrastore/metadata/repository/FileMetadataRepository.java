package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link FileMetadata} entities.
 * Spring Data JPA generates the implementation automatically.
 */
@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    /**
     * Finds all metadata records owned by a given user.
     */
    List<FileMetadata> findByOwner(String owner);

    /**
     * Finds all metadata records owned by a user, paginated.
     */
    Page<FileMetadata> findByOwner(String owner, Pageable pageable);

    /**
     * Finds a metadata record by its content hash (used for deduplication).
     */
    Optional<FileMetadata> findByContentHash(String contentHash);

    /**
     * Checks whether a file with the given content hash already exists.
     */
    boolean existsByContentHash(String contentHash);

    /**
     * Finds a metadata record by its storage location.
     */
    Optional<FileMetadata> findByStorageLocation(String storageLocation);
}

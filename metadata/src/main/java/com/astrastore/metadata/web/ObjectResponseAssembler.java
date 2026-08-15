package com.astrastore.metadata.web;

import com.astrastore.metadata.dto.object.ObjectResponse;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ReplicationStatus;
import com.astrastore.metadata.repository.ChunkLocationRepository;
import com.astrastore.metadata.service.StarService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds {@link ObjectResponse}s for a whole page at a time.
 *
 * <p>The per-row version of this cost three queries per object — two chunk
 * counts and a star lookup — so a 50-row listing issued 150 statements. Both
 * lookups are now one grouped query each, per page, regardless of page size.
 */
@Component
@RequiredArgsConstructor
public class ObjectResponseAssembler {

    private final ChunkLocationRepository chunkLocationRepository;
    private final StarService starService;

    public Page<ObjectResponse> toPage(Page<ObjectRecord> page, Long userId) {
        List<ObjectResponse> content = toList(page.getContent(), userId);
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    public ObjectResponse toResponse(ObjectRecord record, Long userId) {
        return toList(List.of(record), userId).get(0);
    }

    public List<ObjectResponse> toList(List<ObjectRecord> records, Long userId) {
        if (records.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = records.stream().map(ObjectRecord::getId).toList();
        Map<UUID, long[]> chunkCounts = chunkCounts(ids);
        Set<UUID> starred = starService.starredAmong(userId, ids);

        return records.stream()
                .map(record -> {
                    long[] counts = chunkCounts.getOrDefault(record.getId(), new long[] { 0L, 0L });
                    return new ObjectResponse(
                            record.getId(),
                            record.getBucket() != null ? record.getBucket().getId() : null,
                            record.getKey(),
                            record.getSizeBytes(),
                            record.getChecksum(),
                            record.getContentType(),
                            record.getStatus(),
                            record.getCreatedAt(),
                            counts[1],
                            counts[0],
                            starred.contains(record.getId()),
                            record.getDeletedAt());
                })
                .toList();
    }

    /** @return objectId -> {@code [total, replicated]} */
    private Map<UUID, long[]> chunkCounts(List<UUID> objectIds) {
        Map<UUID, long[]> counts = new HashMap<>();
        List<Object[]> rows = chunkLocationRepository.countChunksGrouped(
                objectIds, ReplicationStatus.REPLICATED, ReplicationStatus.COMPLETE);
        for (Object[] row : rows) {
            UUID objectId = (UUID) row[0];
            long total = ((Number) row[1]).longValue();
            long replicated = row[2] == null ? 0L : ((Number) row[2]).longValue();
            counts.put(objectId, new long[] { total, replicated });
        }
        return counts;
    }
}

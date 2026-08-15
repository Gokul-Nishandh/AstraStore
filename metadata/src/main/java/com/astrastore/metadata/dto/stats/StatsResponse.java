package com.astrastore.metadata.dto.stats;

/**
 * The caller's own totals, computed in the database.
 *
 * <p>The dashboard used to derive these by walking a paginated listing, which
 * meant the headline numbers only ever described the first page.
 *
 * @param objectCount   active objects across every bucket the caller owns
 * @param totalBytes    sum of {@code sizeBytes} over those active objects
 * @param bucketCount   buckets the caller owns
 * @param starredCount  active objects the caller has starred
 * @param trashedCount  soft-deleted objects awaiting restore or purge
 */
public record StatsResponse(

        long objectCount,

        long totalBytes,

        long bucketCount,

        long starredCount,

        long trashedCount

) {
}

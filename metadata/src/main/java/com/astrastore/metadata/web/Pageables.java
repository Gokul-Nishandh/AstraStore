package com.astrastore.metadata.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rewrites a client-supplied {@link Pageable} into one that is safe to hand to
 * JPA.
 *
 * <p>Spring binds {@code ?sort=} straight into a {@code Sort}, so an
 * unsanitised handler will pass whatever the caller typed to Hibernate as a
 * property path. That is both a 500 waiting to happen ({@code ?sort=nope}) and
 * a way to sort by, or probe for, columns the API never meant to expose.
 * Only the aliases in the supplied map survive; anything else is dropped.
 *
 * <p>Page size is clamped too — an unbounded {@code ?size=} is a cheap way to
 * pull a whole table in one request.
 */
public final class Pageables {

    /** Sort keys accepted on object listings, mapped to entity properties. */
    public static final Map<String, String> OBJECT_SORTS = Map.of(
            "key", "key",
            "name", "key",
            "size", "sizeBytes",
            "sizebytes", "sizeBytes",
            "createdat", "createdAt",
            "created", "createdAt",
            "deletedat", "deletedAt",
            "status", "status",
            "contenttype", "contentType");

    /**
     * Sort keys accepted on the admin chunk listing.
     *
     * <p>Deliberately short. The listing joins nothing, so only the chunk
     * row's own columns can be sorted on — {@code key} or {@code size} would
     * name properties {@code ChunkLocation} does not have and Hibernate would
     * fail the query rather than ignore them.
     */
    public static final Map<String, String> CHUNK_SORTS = Map.of(
            "createdat", "createdAt",
            "created", "createdAt",
            "chunkindex", "chunkIndex",
            "index", "chunkIndex",
            "node", "nodeId",
            "nodeid", "nodeId",
            "status", "replicationStatus",
            "replicationstatus", "replicationStatus");

    /** Sort keys accepted on the bucket listing. */
    public static final Map<String, String> BUCKET_SORTS = Map.of(
            "name", "name",
            "createdat", "createdAt",
            "created", "createdAt");

    public static final int MAX_PAGE_SIZE = 200;

    private Pageables() {}

    public static Pageable sanitize(Pageable requested,
                                    Map<String, String> allowedSorts,
                                    Sort fallback) {
        int size = Math.max(1, Math.min(requested.getPageSize(), MAX_PAGE_SIZE));
        int page = Math.max(0, requested.getPageNumber());
        return PageRequest.of(page, size, sanitizeSort(requested.getSort(), allowedSorts, fallback));
    }

    private static Sort sanitizeSort(Sort requested, Map<String, String> allowedSorts, Sort fallback) {
        if (requested == null || requested.isUnsorted()) return fallback;

        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : requested) {
            String mapped = allowedSorts.get(order.getProperty().toLowerCase());
            if (mapped != null) {
                orders.add(new Sort.Order(order.getDirection(), mapped));
            }
        }
        return orders.isEmpty() ? fallback : Sort.by(orders);
    }

    /** Trims a {@code ?search=} value; blank and absent are the same thing. */
    public static String normalizeSearch(String search) {
        if (search == null) return null;
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

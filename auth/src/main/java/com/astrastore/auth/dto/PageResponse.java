/**
 * The paginated envelope every list endpoint in this service returns.
 *
 * <p>The old audit endpoint returned a bare JSON array while accepting
 * {@code page} and {@code size}, so the UI had no way to know how many pages
 * existed and could only render a "next" button that might lead nowhere.
 * Total counts are part of the response for exactly that reason.
 */
package com.astrastore.auth.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return ofMapped(page, page.getContent().stream().map(mapper).toList());
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }

    /**
     * For content projected outside the repository — an audit page whose rows
     * have been enriched with the acting user's name, for instance. The
     * pagination metadata still comes from the real {@link Page}.
     */
    public static <E, T> PageResponse<T> ofMapped(Page<E> page, List<T> mapped) {
        return new PageResponse<>(
                mapped,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                describe(page.getSort())
        );
    }

    /**
     * Renders the applied sort in the same {@code property,direction} form the
     * client sent, so a UI can echo it back verbatim on the next request.
     */
    private static String describe(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "unsorted";
        }
        return StreamSupport.stream(sort.spliterator(), false)
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .collect(Collectors.joining(";"));
    }
}

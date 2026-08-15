package com.astrastore.metadata.dto.stats;

import java.util.List;

/**
 * What the caller stores, broken down for charting.
 *
 * @param byCategory  bytes and counts per broad file category, largest first
 * @param daily       objects and bytes written per day over the requested window
 */
public record StorageBreakdown(
        List<CategorySlice> byCategory,
        List<DailyPoint> daily
) {

    /**
     * One file category. Raw MIME types are collapsed into the same handful of
     * categories the console already uses for file icons — a chart with a
     * separate slice for every Office MIME string is unreadable, and the
     * question a user is asking is "what is taking up my space", not "which
     * exact media types do I hold".
     */
    public record CategorySlice(String category, long objectCount, long totalBytes) {}

    /** One day's uploads. Days with no activity are absent, never zero-filled. */
    public record DailyPoint(String date, long objectCount, long totalBytes) {}
}

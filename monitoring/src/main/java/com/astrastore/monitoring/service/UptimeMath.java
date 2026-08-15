package com.astrastore.monitoring.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The availability arithmetic, kept free of Spring and of the database so the
 * numbers on the dashboard can be pinned down by tests.
 *
 * <p>Every method here returns {@code null} rather than a stand-in when the
 * inputs cannot support an answer. A percentage is a claim about a period, and
 * a claim we cannot support must be absent, not zero and not a hundred.
 */
public final class UptimeMath {

    private UptimeMath() {
    }

    /** An outage as far as the arithmetic is concerned; {@code endMillis} null means ongoing. */
    public record Outage(long startMillis, Long endMillis) {
    }

    /**
     * Downtime inside {@code [fromMillis, toMillis]}, clipping each outage to
     * the window so an outage that began last week contributes only the part
     * that falls inside today.
     *
     * <p>Overlapping outages for the same service are merged; double counting
     * them could push downtime past the length of the window itself.
     */
    public static long downtimeSeconds(List<Outage> outages, long fromMillis, long toMillis) {
        if (outages == null || outages.isEmpty() || toMillis <= fromMillis) {
            return 0L;
        }

        List<long[]> clipped = outages.stream()
                .map(o -> new long[]{
                        Math.max(o.startMillis(), fromMillis),
                        Math.min(o.endMillis() == null ? toMillis : o.endMillis(), toMillis)})
                .filter(range -> range[1] > range[0])
                .sorted((a, b) -> Long.compare(a[0], b[0]))
                .toList();

        long total = 0L;
        long spanStart = Long.MIN_VALUE;
        long spanEnd = Long.MIN_VALUE;
        for (long[] range : clipped) {
            if (spanEnd == Long.MIN_VALUE) {
                spanStart = range[0];
                spanEnd = range[1];
            } else if (range[0] <= spanEnd) {
                spanEnd = Math.max(spanEnd, range[1]);
            } else {
                total += spanEnd - spanStart;
                spanStart = range[0];
                spanEnd = range[1];
            }
        }
        if (spanEnd != Long.MIN_VALUE) {
            total += spanEnd - spanStart;
        }
        return total / 1000L;
    }

    /**
     * Availability over the period actually observed.
     *
     * <p>The denominator is observed time, not window length. If probing began
     * an hour ago, a 24-hour window describes that hour and the caller is told
     * as much through {@code observedSeconds} — inflating the denominator to
     * 24 hours would silently credit us for 23 hours we never watched.
     *
     * @return null when nothing was observed, so the caller cannot mistake an
     *         absence of measurement for perfect availability
     */
    public static Double uptimePercent(long observedSeconds, long downtimeSeconds) {
        if (observedSeconds <= 0) {
            return null;
        }
        long down = Math.max(0L, Math.min(downtimeSeconds, observedSeconds));
        double percent = 100.0 * (observedSeconds - down) / observedSeconds;
        return round2(percent);
    }

    /**
     * Cluster availability, weighted by how long each service was observed.
     *
     * <p>A plain mean of per-service percentages would let a service watched
     * for two minutes count as much as one watched for a month.
     *
     * @return null when no service contributed any observed time
     */
    public static Double weightedUptimePercent(long totalObservedSeconds, long totalDowntimeSeconds) {
        return uptimePercent(totalObservedSeconds, totalDowntimeSeconds);
    }

    public static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}

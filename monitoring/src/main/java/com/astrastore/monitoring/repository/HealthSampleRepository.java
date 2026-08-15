package com.astrastore.monitoring.repository;

import com.astrastore.monitoring.entity.HealthSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Sample access. Every window query aggregates in SQL — a 30-day window holds
 * roughly 170k rows per service, which must never be pulled into the JVM to
 * answer one dashboard request.
 */
public interface HealthSampleRepository extends JpaRepository<HealthSample, Long> {

    Optional<HealthSample> findFirstByServiceIdOrderByProbedAtMillisDesc(String serviceId);

    /**
     * The newest probe that actually succeeded. The newest probe overall may
     * be a refused connection, whose sub-millisecond "response time" would be
     * shown as the service's latest latency.
     */
    Optional<HealthSample> findFirstByServiceIdAndUpTrueOrderByProbedAtMillisDesc(String serviceId);

    /**
     * Per service: sample count, successful count, and the observed span.
     *
     * <p>The span matters as much as the counts: it is the denominator for
     * uptime, and using it instead of the nominal window length is what stops
     * a monitor that started ten minutes ago from claiming to describe 24
     * hours.
     *
     * @return rows of {@code [serviceId, total, upCount, firstMillis, lastMillis]}
     */
    @Query("""
            select s.serviceId,
                   count(s),
                   sum(case when s.up = true then 1L else 0L end),
                   min(s.probedAtMillis),
                   max(s.probedAtMillis)
              from HealthSample s
             where s.probedAtMillis >= :fromMillis
               and s.probedAtMillis <= :toMillis
             group by s.serviceId
            """)
    List<Object[]> aggregateByService(@Param("fromMillis") long fromMillis,
                                      @Param("toMillis") long toMillis);

    /**
     * Response-time percentiles over successful probes only — a refused
     * connection returns in a millisecond and would otherwise drag p50 down
     * exactly when the service is at its worst.
     *
     * @return rows of {@code [serviceId, p50, p95, p99]}
     */
    @Query(value = """
            SELECT service_id,
                   PERCENTILE_DISC(0.5)  WITHIN GROUP (ORDER BY response_time_ms),
                   PERCENTILE_DISC(0.95) WITHIN GROUP (ORDER BY response_time_ms),
                   PERCENTILE_DISC(0.99) WITHIN GROUP (ORDER BY response_time_ms)
              FROM monitoring.health_samples
             WHERE is_up = TRUE
               AND probed_at_millis >= :fromMillis
               AND probed_at_millis <= :toMillis
             GROUP BY service_id
            """, nativeQuery = true)
    List<Object[]> responseTimePercentiles(@Param("fromMillis") long fromMillis,
                                           @Param("toMillis") long toMillis);

    /**
     * Fixed-width buckets for the sparkline.
     *
     * <p>A bucket counts as up only if every sample in it was up, so a blip
     * inside a twelve-hour bucket is visible rather than averaged away.
     * Buckets with no samples are simply absent from the result — the caller
     * must not draw a point where nothing was observed.
     *
     * <p>Grouped by ordinal rather than by repeating the bucket expression:
     * each named parameter is expanded into its own positional marker, so the
     * copy of the expression in GROUP BY binds to different placeholders than
     * the one in SELECT and Postgres rejects the query as not grouped.
     *
     * @return rows of {@code [serviceId, bucketIndex, allUp, avgMs]}
     */
    @Query(value = """
            SELECT service_id,
                   (probed_at_millis - :fromMillis) / :bucketMillis,
                   MIN(CASE WHEN is_up = TRUE THEN 1 ELSE 0 END),
                   AVG(CAST(CASE WHEN is_up = TRUE THEN response_time_ms END AS DOUBLE PRECISION))
              FROM monitoring.health_samples
             WHERE probed_at_millis >= :fromMillis
               AND probed_at_millis <= :toMillis
             GROUP BY 1, 2
             ORDER BY 1, 2
            """, nativeQuery = true)
    List<Object[]> sparklineBuckets(@Param("fromMillis") long fromMillis,
                                    @Param("toMillis") long toMillis,
                                    @Param("bucketMillis") long bucketMillis);

    /** Bulk delete; a derived {@code deleteBy} would load every row first. */
    @Modifying
    @Query("delete from HealthSample s where s.probedAtMillis < :cutoffMillis")
    int deleteOlderThan(@Param("cutoffMillis") long cutoffMillis);
}

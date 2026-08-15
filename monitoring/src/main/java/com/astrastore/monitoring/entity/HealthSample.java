package com.astrastore.monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One probe result.
 *
 * <p>This table is the high-volume one — every target, every interval,
 * forever — so it carries no relations and is trimmed by a retention job.
 * Nothing on the read path scans it row by row; the aggregate queries in
 * {@code HealthSampleRepository} push counting, percentiles and bucketing
 * into the database.
 *
 * <p>The timestamp is stored as epoch milliseconds rather than a SQL
 * timestamp so that bucketing (integer division) and range filters are plain
 * arithmetic, identical on PostgreSQL and on H2 in tests.
 */
@Entity
@Table(name = "health_samples", schema = "monitoring", indexes = {
        @Index(name = "idx_health_samples_service_time", columnList = "service_id, probed_at_millis"),
        @Index(name = "idx_health_samples_probed_at", columnList = "probed_at_millis")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "service_id", length = 64, nullable = false)
    private String serviceId;

    @Column(name = "probed_at_millis", nullable = false)
    private long probedAtMillis;

    /** True only when the target answered 2xx <em>and</em> reported itself UP. */
    @Column(name = "is_up", nullable = false)
    private boolean up;

    /** Wall-clock cost of the probe, recorded for failures as well as successes. */
    @Column(name = "response_time_ms", nullable = false)
    private int responseTimeMs;

    /** Null when the connection never produced a response. */
    @Column(name = "http_status")
    private Integer httpStatus;

    /** The {@code status} field from the actuator body, when one was parsed. */
    @Column(name = "reported_status", length = 32)
    private String reportedStatus;

    public Instant probedAt() {
        return Instant.ofEpochMilli(probedAtMillis);
    }
}

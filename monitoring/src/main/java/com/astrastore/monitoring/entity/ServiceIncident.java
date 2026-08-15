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
 * A confirmed outage: one row per UP→DOWN transition, closed on the way back.
 *
 * <p>Downtime is answered from this table rather than by scanning samples.
 * A month of samples for one service is six figures of rows; the transitions
 * between them are a handful, and they are what the question "how long was it
 * down" is actually about.
 */
@Entity
@Table(name = "service_incidents", schema = "monitoring", indexes = {
        @Index(name = "idx_service_incidents_service", columnList = "service_id, started_at_millis"),
        @Index(name = "idx_service_incidents_open", columnList = "ended_at_millis")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "service_id", length = 64, nullable = false)
    private String serviceId;

    /**
     * When the outage began — the timestamp of the <em>first</em> failing
     * probe, not the one that crossed the debounce threshold, so a threshold
     * of two does not shorten every recorded outage by one interval.
     */
    @Column(name = "started_at_millis", nullable = false)
    private long startedAtMillis;

    /** Null while the outage is in progress. */
    @Column(name = "ended_at_millis")
    private Long endedAtMillis;

    /** Written once on close; ongoing incidents are measured against now. */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "last_error", length = 512)
    private String lastError;

    public boolean isOngoing() {
        return endedAtMillis == null;
    }

    public Instant startedAt() {
        return Instant.ofEpochMilli(startedAtMillis);
    }

    public Instant endedAt() {
        return endedAtMillis == null ? null : Instant.ofEpochMilli(endedAtMillis);
    }

    /** Elapsed seconds, measured to {@code nowMillis} while still open. */
    public long durationSeconds(long nowMillis) {
        if (durationSeconds != null) {
            return durationSeconds;
        }
        long end = endedAtMillis != null ? endedAtMillis : nowMillis;
        return Math.max(0L, (end - startedAtMillis) / 1000L);
    }
}

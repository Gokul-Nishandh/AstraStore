package com.astrastore.monitoring.domain;

/**
 * What the monitor can currently say about a service.
 *
 * <p>{@link #UNKNOWN} is a real answer, not a placeholder: it is what we
 * report when nothing has been observed recently enough to have an opinion.
 * Collapsing it into {@link #DOWN} would invent an outage, and collapsing it
 * into {@link #UP} would hide one.
 */
public enum ServiceStatus {

    /** Newest probe succeeded and no outage is in progress. */
    UP,

    /** Failures passed the debounce threshold; an incident is open. */
    DOWN,

    /**
     * Something is off but not confirmed: a failing probe that has not yet
     * reached the failure threshold, a recovering probe that has not yet
     * reached the success threshold, or a reachable service that reports a
     * health status other than UP.
     */
    DEGRADED,

    /** No sample, or none recent enough to be meaningful. */
    UNKNOWN
}

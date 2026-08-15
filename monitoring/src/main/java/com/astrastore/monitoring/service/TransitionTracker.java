package com.astrastore.monitoring.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides when a run of probe results amounts to an actual state change.
 *
 * <p>Raw probe results are noisy: a dropped packet, a container's brief pause
 * during a GC or a rolling restart all produce one failed probe against a
 * service that was never really unavailable. Opening an incident for each of
 * those would fill the history with outages that did not happen — and the
 * history is permanent, so a false incident is worse than a late one.
 * Requiring N consecutive results in the same direction trades a delay of
 * (N-1) intervals for that accuracy.
 *
 * <p>Pure state, no I/O: the caller persists whatever {@link Decision} comes
 * back.
 */
public class TransitionTracker {

    private final int failureThreshold;
    private final int successThreshold;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public TransitionTracker(int failureThreshold, int successThreshold) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.successThreshold = Math.max(1, successThreshold);
    }

    public enum Decision {
        /** Nothing to record — either steady state or a run still building. */
        NONE,
        OPEN_INCIDENT,
        CLOSE_INCIDENT
    }

    /**
     * Seeds a service's confirmed state, called at startup from the presence
     * of an open incident. Without it a monitor restarted mid-outage would
     * treat the first failure as fresh and open a duplicate incident on top of
     * the one already running.
     */
    public void seed(String serviceId, boolean confirmedDown) {
        states.compute(serviceId, (id, existing) -> {
            State state = existing == null ? new State() : existing;
            state.confirmedDown = confirmedDown;
            state.consecutiveFailures = 0;
            state.consecutiveSuccesses = 0;
            state.firstFailureMillis = null;
            state.firstSuccessMillis = null;
            return state;
        });
    }

    /**
     * @param probedAtMillis timestamp of this probe; retained as the incident
     *                       start when this result begins a failing run, so an
     *                       incident is dated from the first failure rather
     *                       than from the one that crossed the threshold
     */
    public synchronized Decision record(String serviceId, boolean up, long probedAtMillis) {
        State state = states.computeIfAbsent(serviceId, id -> new State());

        if (up) {
            state.consecutiveFailures = 0;
            state.firstFailureMillis = null;
            state.consecutiveSuccesses++;
            if (state.firstSuccessMillis == null) {
                state.firstSuccessMillis = probedAtMillis;
            }
            if (state.confirmedDown && state.consecutiveSuccesses >= successThreshold) {
                state.confirmedDown = false;
                state.consecutiveSuccesses = 0;
                return Decision.CLOSE_INCIDENT;
            }
            return Decision.NONE;
        }

        state.consecutiveSuccesses = 0;
        state.firstSuccessMillis = null;
        state.consecutiveFailures++;
        if (state.firstFailureMillis == null) {
            state.firstFailureMillis = probedAtMillis;
        }
        if (!state.confirmedDown && state.consecutiveFailures >= failureThreshold) {
            state.confirmedDown = true;
            return Decision.OPEN_INCIDENT;
        }
        return Decision.NONE;
    }

    /** When the current failing run began, or null if the service is not failing. */
    public Long firstFailureMillis(String serviceId) {
        State state = states.get(serviceId);
        return state == null ? null : state.firstFailureMillis;
    }

    /**
     * When the current recovering run began. An incident ends at the first
     * probe that succeeded, not at the one that satisfied the threshold —
     * otherwise every recovery would be recorded (N-1) intervals late.
     */
    public Long firstSuccessMillis(String serviceId) {
        State state = states.get(serviceId);
        return state == null ? null : state.firstSuccessMillis;
    }

    public boolean isConfirmedDown(String serviceId) {
        State state = states.get(serviceId);
        return state != null && state.confirmedDown;
    }

    private static final class State {
        private boolean confirmedDown;
        private int consecutiveFailures;
        private int consecutiveSuccesses;
        private Long firstFailureMillis;
        private Long firstSuccessMillis;
    }
}

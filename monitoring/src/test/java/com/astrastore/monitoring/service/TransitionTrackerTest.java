package com.astrastore.monitoring.service;

import com.astrastore.monitoring.service.TransitionTracker.Decision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransitionTrackerTest {

    private static final String SERVICE = "auth";

    @Test
    void singleFailureDoesNotOpenAnIncident() {
        TransitionTracker tracker = new TransitionTracker(2, 2);

        assertThat(tracker.record(SERVICE, false, 1_000)).isEqualTo(Decision.NONE);
        assertThat(tracker.isConfirmedDown(SERVICE)).isFalse();
    }

    @Test
    void oneFailureFollowedByRecoveryIsNotAnIncident() {
        TransitionTracker tracker = new TransitionTracker(2, 2);

        tracker.record(SERVICE, false, 1_000);
        assertThat(tracker.record(SERVICE, true, 2_000)).isEqualTo(Decision.NONE);
        assertThat(tracker.isConfirmedDown(SERVICE)).isFalse();
    }

    @Test
    void twoConsecutiveFailuresOpenAnIncident() {
        TransitionTracker tracker = new TransitionTracker(2, 2);

        assertThat(tracker.record(SERVICE, false, 1_000)).isEqualTo(Decision.NONE);
        assertThat(tracker.record(SERVICE, false, 2_000)).isEqualTo(Decision.OPEN_INCIDENT);
        assertThat(tracker.isConfirmedDown(SERVICE)).isTrue();
    }

    @Test
    void incidentIsDatedFromTheFirstFailureNotTheThresholdCrossing() {
        TransitionTracker tracker = new TransitionTracker(3, 2);

        tracker.record(SERVICE, false, 1_000);
        tracker.record(SERVICE, false, 2_000);
        assertThat(tracker.record(SERVICE, false, 3_000)).isEqualTo(Decision.OPEN_INCIDENT);
        assertThat(tracker.firstFailureMillis(SERVICE)).isEqualTo(1_000L);
    }

    @Test
    void furtherFailuresDoNotReopenAnAlreadyOpenIncident() {
        TransitionTracker tracker = new TransitionTracker(2, 2);

        tracker.record(SERVICE, false, 1_000);
        tracker.record(SERVICE, false, 2_000);

        assertThat(tracker.record(SERVICE, false, 3_000)).isEqualTo(Decision.NONE);
        assertThat(tracker.record(SERVICE, false, 4_000)).isEqualTo(Decision.NONE);
    }

    @Test
    void oneSuccessDoesNotCloseAnIncidentWhenTwoAreRequired() {
        TransitionTracker tracker = new TransitionTracker(2, 2);
        tracker.record(SERVICE, false, 1_000);
        tracker.record(SERVICE, false, 2_000);

        assertThat(tracker.record(SERVICE, true, 3_000)).isEqualTo(Decision.NONE);
        assertThat(tracker.isConfirmedDown(SERVICE)).isTrue();
    }

    @Test
    void twoConsecutiveSuccessesCloseTheIncidentDatedFromTheFirstOfThem() {
        TransitionTracker tracker = new TransitionTracker(2, 2);
        tracker.record(SERVICE, false, 1_000);
        tracker.record(SERVICE, false, 2_000);

        tracker.record(SERVICE, true, 3_000);
        assertThat(tracker.record(SERVICE, true, 4_000)).isEqualTo(Decision.CLOSE_INCIDENT);
        assertThat(tracker.firstSuccessMillis(SERVICE)).isEqualTo(3_000L);
        assertThat(tracker.isConfirmedDown(SERVICE)).isFalse();
    }

    @Test
    void aFailureInsideTheRecoveryRunRestartsIt() {
        TransitionTracker tracker = new TransitionTracker(2, 3);
        tracker.record(SERVICE, false, 1_000);
        tracker.record(SERVICE, false, 2_000);

        tracker.record(SERVICE, true, 3_000);
        tracker.record(SERVICE, true, 4_000);
        assertThat(tracker.record(SERVICE, false, 5_000)).isEqualTo(Decision.NONE);

        tracker.record(SERVICE, true, 6_000);
        tracker.record(SERVICE, true, 7_000);
        assertThat(tracker.record(SERVICE, true, 8_000)).isEqualTo(Decision.CLOSE_INCIDENT);
        assertThat(tracker.firstSuccessMillis(SERVICE)).isEqualTo(6_000L);
    }

    @Test
    void successesWhileHealthyNeverProduceACloseDecision() {
        TransitionTracker tracker = new TransitionTracker(2, 2);

        assertThat(tracker.record(SERVICE, true, 1_000)).isEqualTo(Decision.NONE);
        assertThat(tracker.record(SERVICE, true, 2_000)).isEqualTo(Decision.NONE);
        assertThat(tracker.record(SERVICE, true, 3_000)).isEqualTo(Decision.NONE);
    }

    @Test
    void seedingAsDownStopsARestartFromOpeningADuplicateIncident() {
        TransitionTracker tracker = new TransitionTracker(2, 2);
        tracker.seed(SERVICE, true);

        assertThat(tracker.record(SERVICE, false, 1_000)).isEqualTo(Decision.NONE);
        assertThat(tracker.record(SERVICE, false, 2_000)).isEqualTo(Decision.NONE);

        tracker.record(SERVICE, true, 3_000);
        assertThat(tracker.record(SERVICE, true, 4_000)).isEqualTo(Decision.CLOSE_INCIDENT);
    }

    @Test
    void thresholdOfOneOpensOnTheFirstFailure() {
        TransitionTracker tracker = new TransitionTracker(1, 1);

        assertThat(tracker.record(SERVICE, false, 1_000)).isEqualTo(Decision.OPEN_INCIDENT);
        assertThat(tracker.record(SERVICE, true, 2_000)).isEqualTo(Decision.CLOSE_INCIDENT);
    }

    @Test
    void servicesAreTrackedIndependently() {
        TransitionTracker tracker = new TransitionTracker(2, 2);

        tracker.record("auth", false, 1_000);
        tracker.record("upload", false, 1_000);
        assertThat(tracker.record("auth", false, 2_000)).isEqualTo(Decision.OPEN_INCIDENT);

        assertThat(tracker.isConfirmedDown("upload")).isFalse();
        assertThat(tracker.isConfirmedDown("metadata")).isFalse();
    }
}

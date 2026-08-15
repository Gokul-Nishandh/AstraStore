package com.astrastore.monitoring.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UptimeMathTest {

    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;

    // --- uptimePercent ----------------------------------------------------

    @Test
    void uptimePercent_matchesTheWorkedExample() {
        assertThat(UptimeMath.uptimePercent(86_400, 155)).isEqualTo(99.82);
    }

    @Test
    void uptimePercent_isOneHundredWhenNothingWasDownButSomethingWasObserved() {
        assertThat(UptimeMath.uptimePercent(3600, 0)).isEqualTo(100.0);
    }

    @Test
    void uptimePercent_isZeroWhenDownForTheWholeObservedPeriod() {
        assertThat(UptimeMath.uptimePercent(3600, 3600)).isEqualTo(0.0);
    }

    @Test
    void uptimePercent_isNullWhenNothingWasObserved() {
        assertThat(UptimeMath.uptimePercent(0, 0)).isNull();
        assertThat(UptimeMath.uptimePercent(-1, 0)).isNull();
    }

    @Test
    void uptimePercent_neverGoesNegativeWhenDowntimeExceedsObservation() {
        assertThat(UptimeMath.uptimePercent(600, 900)).isEqualTo(0.0);
    }

    @Test
    void uptimePercent_roundsToTwoDecimals() {
        assertThat(UptimeMath.uptimePercent(100_000, 1)).isEqualTo(100.0);
        assertThat(UptimeMath.uptimePercent(1000, 1)).isEqualTo(99.9);
        assertThat(UptimeMath.uptimePercent(3000, 1)).isEqualTo(99.97);
    }

    // --- downtimeSeconds --------------------------------------------------

    @Test
    void downtime_sumsClosedOutagesInsideTheWindow() {
        long now = 10 * DAY;
        long from = now - DAY;

        long seconds = UptimeMath.downtimeSeconds(List.of(
                new UptimeMath.Outage(from + HOUR, from + HOUR + 100_000L),
                new UptimeMath.Outage(from + 5 * HOUR, from + 5 * HOUR + 55_000L)
        ), from, now);

        assertThat(seconds).isEqualTo(155L);
    }

    @Test
    void downtime_clipsAnOutageThatStartedBeforeTheWindow() {
        long now = 10 * DAY;
        long from = now - DAY;

        long seconds = UptimeMath.downtimeSeconds(List.of(
                new UptimeMath.Outage(from - 5 * HOUR, from + HOUR)
        ), from, now);

        assertThat(seconds).isEqualTo(HOUR / 1000);
    }

    @Test
    void downtime_measuresAnOngoingOutageToTheEndOfTheWindow() {
        long now = 10 * DAY;
        long from = now - DAY;

        long seconds = UptimeMath.downtimeSeconds(List.of(
                new UptimeMath.Outage(now - 2 * HOUR, null)
        ), from, now);

        assertThat(seconds).isEqualTo(2 * HOUR / 1000);
    }

    @Test
    void downtime_ignoresAnOutageThatEndedBeforeTheWindow() {
        long now = 10 * DAY;
        long from = now - DAY;

        long seconds = UptimeMath.downtimeSeconds(List.of(
                new UptimeMath.Outage(from - 3 * HOUR, from - 2 * HOUR)
        ), from, now);

        assertThat(seconds).isZero();
    }

    @Test
    void downtime_mergesOverlappingOutagesRatherThanCountingBoth() {
        long now = 10 * DAY;
        long from = now - DAY;

        long seconds = UptimeMath.downtimeSeconds(List.of(
                new UptimeMath.Outage(from + HOUR, from + 3 * HOUR),
                new UptimeMath.Outage(from + 2 * HOUR, from + 4 * HOUR)
        ), from, now);

        assertThat(seconds).isEqualTo(3 * HOUR / 1000);
    }

    @Test
    void downtime_isZeroWithNoOutages() {
        assertThat(UptimeMath.downtimeSeconds(List.of(), 0, DAY)).isZero();
        assertThat(UptimeMath.downtimeSeconds(null, 0, DAY)).isZero();
    }

    // --- cluster ----------------------------------------------------------

    @Test
    void clusterUptime_weightsByObservedTimeAndIsNullWithoutObservation() {
        assertThat(UptimeMath.weightedUptimePercent(172_800, 155)).isEqualTo(99.91);
        assertThat(UptimeMath.weightedUptimePercent(0, 0)).isNull();
    }
}

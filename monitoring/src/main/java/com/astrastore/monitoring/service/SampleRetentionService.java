package com.astrastore.monitoring.service;

import com.astrastore.monitoring.config.MonitoringProperties;
import com.astrastore.monitoring.repository.HealthSampleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Trims the sample table.
 *
 * <p>Eleven targets at a 15-second interval is around 63k rows a day and grows
 * forever. Incidents are kept indefinitely — they are small and they are the
 * record that matters — but raw samples past the retention window only feed
 * sparklines and percentiles nobody asks for.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SampleRetentionService {

    private final HealthSampleRepository sampleRepository;
    private final MonitoringProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${astrastore.monitoring.retention-interval-ms:3600000}",
            initialDelayString = "${astrastore.monitoring.retention-initial-delay-ms:60000}")
    @Transactional
    public void purgeExpiredSamples() {
        int days = properties.getRetentionDays();
        if (days <= 0) {
            return;
        }
        long cutoff = clock.millis() - Duration.ofDays(days).toMillis();
        try {
            int deleted = sampleRepository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Deleted {} health samples older than {} days", deleted, days);
            }
        } catch (Exception e) {
            log.error("Sample retention purge failed", e);
        }
    }
}

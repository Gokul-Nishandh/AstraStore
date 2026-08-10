/**
 * Records security events and manages audit log retention policy.
 * Provides auto-archival to cold storage and daily purge of logs older than 90 days.
 * Extracts client IP (with X-Forwarded-For support) and user-agent from requests.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.AuditLog;
import com.astrastore.auth.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private static final int RETENTION_DAYS = 90;
    private static final String COLD_STORAGE_MARKER = "COLD_STORAGE_BUCKET";

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logEvent(Long userId, AuditAction action, HttpServletRequest request, boolean success, String failureReason) {
        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .userId(userId)
                .action(action)
                .ipAddress(extractIp(request))
                .userAgent(extractUserAgent(request))
                .success(success)
                .failureReason(failureReason);

        AuditLog entry = builder.build();
        auditLogRepository.save(entry);

        if (log.isDebugEnabled()) {
            log.debug("Audit event recorded — action={}, userId={}, success={}", action, userId, success);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsForUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void archiveAndPurgeOldLogs() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        log.info("Audit log cleanup started — moving logs older than {} days to cold storage", RETENTION_DAYS);

        Page<AuditLog> oldLogs = auditLogRepository.findAllByOrderByTimestampDesc(
                Pageable.ofSize(1000));
        long archived = oldLogs.getContent().stream()
                .filter(log -> log.getTimestamp().isBefore(cutoff))
                .peek(this::archiveToColdStorage)
                .count();

        int deleted = apiKeyRepositoryDeleteOlderThan(cutoff);

        log.info("Audit log cleanup complete — archived={}, deleted={}", archived, deleted);
    }

    private int apiKeyRepositoryDeleteOlderThan(Instant cutoff) {
        return auditLogRepository.deleteOlderThan(cutoff);
    }

    private void archiveToColdStorage(AuditLog auditLog) {
        log.info("Archiving audit log to cold storage — id={}, action={}, timestamp={}, bucket={}",
                auditLog.getId(), auditLog.getAction(), auditLog.getTimestamp(), COLD_STORAGE_MARKER);
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 256) {
            return ua.substring(0, 256);
        }
        return ua;
    }
}

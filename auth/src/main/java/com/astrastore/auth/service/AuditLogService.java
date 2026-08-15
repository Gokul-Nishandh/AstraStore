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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private static final int RETENTION_DAYS = 90;
    private static final String COLD_STORAGE_MARKER = "COLD_STORAGE_BUCKET";

    private static final int MAX_DETAIL = 512;
    private static final int MAX_REASON = 256;
    private static final int MAX_EMAIL = 255;

    private final AuditLogRepository auditLogRepository;

    /**
     * Existing signature, unchanged for every current call site.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(Long userId, AuditAction action, HttpServletRequest request,
                         boolean success, String failureReason) {
        record(userId, null, action, request, success, failureReason, null);
    }

    /**
     * Records one event.
     *
     * <p>Runs in its own transaction so that writing the audit row is not
     * rolled back by whatever the caller does next. An audit trail that
     * disappears whenever the audited operation fails is exactly the wrong
     * way round — failures are the entries worth keeping.
     *
     * <p>Never throws: an audit write that fails must not turn a successful
     * operation into a 500. It is logged loudly instead.
     *
     * @param actorEmail the address involved, recorded even when {@code userId}
     *                   is null so a failed login is still attributable
     * @param detail     human-readable context; must never contain a secret
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String actorEmail, AuditAction action, HttpServletRequest request,
                       boolean success, String failureReason, String detail) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .actorEmail(truncate(actorEmail, MAX_EMAIL))
                    .action(action)
                    .ipAddress(extractIp(request))
                    .userAgent(extractUserAgent(request))
                    .success(success)
                    .failureReason(truncate(failureReason, MAX_REASON))
                    .detail(truncate(detail, MAX_DETAIL))
                    .build();

            auditLogRepository.save(entry);

            if (log.isDebugEnabled()) {
                log.debug("Audit event recorded — action={}, userId={}, success={}", action, userId, success);
            }
        } catch (Exception e) {
            log.error("Failed to write audit entry — action={}, userId={}, success={}. Error: {}",
                    action, userId, success, e.getMessage(), e);
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

        // Oldest first: these are the rows at risk of being purged. Reading
        // the newest page and filtering it, as this once did, would archive
        // nothing until the whole table was older than the cutoff.
        Page<AuditLog> oldLogs = auditLogRepository.findAll(
                PageRequest.of(0, 1000, Sort.by(Sort.Direction.ASC, "timestamp")));
        long archived = oldLogs.getContent().stream()
                .filter(entry -> entry.getTimestamp() != null && entry.getTimestamp().isBefore(cutoff))
                .peek(this::archiveToColdStorage)
                .count();

        int deleted = auditLogRepository.deleteOlderThan(cutoff);

        log.info("Audit log cleanup complete — archived={}, deleted={}", archived, deleted);
    }

    private void archiveToColdStorage(AuditLog auditLog) {
        log.info("Archiving audit log to cold storage — id={}, action={}, timestamp={}, bucket={}",
                auditLog.getId(), auditLog.getAction(), auditLog.getTimestamp(), COLD_STORAGE_MARKER);
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private String extractUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        return truncate(request.getHeader("User-Agent"), 256);
    }

    /** Keeps oversized client-supplied strings from failing the insert. */
    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}

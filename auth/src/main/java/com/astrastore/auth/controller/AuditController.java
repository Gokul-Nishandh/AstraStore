/**
 * Reads the audit trail: a filtered, paginated view and a CSV export of the
 * same query.
 *
 * <p>Scope is decided in exactly one place —
 * {@link AuditQuery#effectiveUserId(Long, Long, boolean)} — and every handler
 * here routes through it. A non-administrator is pinned to their own id
 * whatever {@code userId} they send; an administrator may narrow to one user
 * or omit the parameter for a cluster-wide view.
 */
package com.astrastore.auth.controller;

import com.astrastore.auth.dto.AuditLogResponse;
import com.astrastore.auth.dto.PageResponse;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.security.CallerContext;
import com.astrastore.auth.service.AuditLogService;
import com.astrastore.auth.service.AuditQuery;
import com.astrastore.auth.service.AuditQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private static final DateTimeFormatter FILENAME_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC);

    private final AuditQueryService auditQueryService;
    private final AuditLogService auditLogService;
    private final CallerContext callerContext;

    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {

        User caller = callerContext.requireUser();
        boolean admin = callerContext.isAdmin(caller);
        Long scope = AuditQuery.effectiveUserId(userId, caller.getId(), admin);

        AuditQuery query = new AuditQuery(
                userId, parseAction(action), parseOutcome(outcome),
                parseInstant(from, "from"), parseInstant(to, "to"), search);

        PageResponse<AuditLogResponse> result =
                auditQueryService.search(query, scope, auditQueryService.toPageable(page, size, sort));

        recordCrossUserRead(caller, scope, admin, httpRequest);

        return ResponseEntity.ok(result);
    }

    /**
     * The action values present in the rows this caller may see, for the
     * filter dropdown. Scoped like the listing, so the set of actions itself
     * cannot be used to infer what another account has been doing.
     */
    @GetMapping("/actions")
    public ResponseEntity<List<AuditAction>> actions(
            @RequestParam(required = false) Long userId) {

        User caller = callerContext.requireUser();
        Long scope = AuditQuery.effectiveUserId(
                userId, caller.getId(), callerContext.isAdmin(caller));
        return ResponseEntity.ok(auditQueryService.distinctActions(scope));
    }

    /**
     * Streams the same query as CSV.
     *
     * <p>Written straight to the servlet output stream rather than buffered
     * into a byte array: an administrator's cluster-wide export runs to tens
     * of thousands of rows, and materialising them all would cost heap
     * proportional to the table.
     */
    @GetMapping("/export.csv")
    public void exportCsv(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) throws IOException {

        User caller = callerContext.requireUser();
        boolean admin = callerContext.isAdmin(caller);
        Long scope = AuditQuery.effectiveUserId(userId, caller.getId(), admin);

        AuditQuery query = new AuditQuery(
                userId, parseAction(action), parseOutcome(outcome),
                parseInstant(from, "from"), parseInstant(to, "to"), search);

        // Every export is recorded, unlike a page view: taking a copy of the
        // trail off the platform is an event an operator should be able to
        // find later, and exports are rare enough not to drown the table.
        auditLogService.record(caller.getId(), caller.getEmail(), AuditAction.AUDIT_LOG_EXPORTED,
                httpRequest, true, null,
                "Exported audit trail as CSV — scope="
                        + (scope == null ? "all users" : "userId=" + scope));

        // Headers are set before the first byte because once the body starts
        // the response is committed and GlobalExceptionHandler can no longer
        // replace it with an ApiError.
        httpResponse.setStatus(HttpServletResponse.SC_OK);
        httpResponse.setContentType("text/csv; charset=UTF-8");
        httpResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"astrastore-audit-" + FILENAME_STAMP.format(Instant.now()) + ".csv\"");

        auditQueryService.writeCsv(query, scope, auditQueryService.toSort(sort),
                httpResponse.getOutputStream());
    }

    // --- helpers -----------------------------------------------------------

    /**
     * Records a read only when an administrator looked past their own rows.
     *
     * <p>Auditing every page view would make the trail mostly a record of
     * itself being read: each visit to the console writes a row that appears
     * in the next visit, and the signal an operator is looking for drowns.
     * Reading somebody else's history is the part worth keeping.
     */
    private void recordCrossUserRead(User caller, Long scope, boolean admin,
                                     HttpServletRequest httpRequest) {
        if (!admin || (scope != null && scope.equals(caller.getId()))) {
            return;
        }
        auditLogService.record(caller.getId(), caller.getEmail(), AuditAction.AUDIT_LOG_VIEWED,
                httpRequest, true, null,
                "Viewed audit trail — scope=" + (scope == null ? "all users" : "userId=" + scope));
    }

    private static AuditAction parseAction(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AuditAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // The enum's own message quotes the fully-qualified class name.
            throw new IllegalArgumentException("Unknown audit action '" + value.trim()
                    + "'. See GET /api/auth/audit/actions for the available values.");
        }
    }

    /**
     * {@code success}, {@code failure} or {@code all}. Null and {@code all}
     * both mean "do not filter", so a UI can bind the dropdown's default
     * option to a real value instead of having to omit the parameter.
     */
    private static Boolean parseOutcome(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "success" -> Boolean.TRUE;
            case "failure" -> Boolean.FALSE;
            case "all" -> null;
            default -> throw new IllegalArgumentException(
                    "outcome must be one of: success, failure, all");
        };
    }

    private static Instant parseInstant(String value, String parameter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    parameter + " must be an ISO-8601 instant, for example 2026-01-31T00:00:00Z");
        }
    }
}

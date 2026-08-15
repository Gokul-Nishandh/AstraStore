/**
 * Reads the audit trail: filtering, pagination, identity resolution and CSV
 * export.
 *
 * <p>Separate from {@link AuditLogService}, which writes it. Writing must be
 * cheap and must never fail an operation; reading is where the joins,
 * ordering rules and access scoping live.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.dto.AuditLogResponse;
import com.astrastore.auth.dto.PageResponse;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.AuditLog;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.repository.AuditLogRepository;
import com.astrastore.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditQueryService {

    /** A page bigger than this is a denial-of-service request, not a query. */
    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Sortable columns. An allow-list, not a pass-through: handing an
     * arbitrary string to {@code Sort.by} lets a caller name a property that
     * does not exist and turn a listing into a 500, and names internal
     * columns in the error while doing it.
     */
    private static final Set<String> SORTABLE = Set.of("timestamp", "action", "success", "id", "ipAddress");
    private static final String DEFAULT_SORT_PROPERTY = "timestamp";

    /** Ceiling on a single CSV export, so one click cannot stream the table forever. */
    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final int EXPORT_CHUNK = 500;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditQuery query, Long effectiveUserId, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findAll(query.toSpecification(effectiveUserId), pageable);
        List<AuditLogResponse> rows = enrich(page.getContent());
        return PageResponse.ofMapped(page, rows);
    }

    /**
     * Attaches the acting user's username and email to each row.
     *
     * <p>One extra query for the whole page, not one per row: the console's
     * default page of 20 entries would otherwise cost 21 round trips.
     */
    private List<AuditLogResponse> enrich(List<AuditLog> entries) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (AuditLog entry : entries) {
            if (entry.getUserId() != null) {
                userIds.add(entry.getUserId());
            }
        }

        Map<Long, User> byId = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findAllById(userIds)) {
                byId.put(user.getId(), user);
            }
        }

        return entries.stream().map(entry -> toResponse(entry, byId.get(entry.getUserId()))).toList();
    }

    private static AuditLogResponse toResponse(AuditLog entry, User user) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getUserId(),
                user != null ? user.getUsername() : null,
                user != null ? user.getEmail() : null,
                // Falls back to the address the caller presented, which is the
                // only identifying thing a pre-identification failure has.
                entry.getActorEmail() != null
                        ? entry.getActorEmail()
                        : (user != null ? user.getEmail() : null),
                entry.getAction(),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.isSuccess(),
                entry.getFailureReason(),
                entry.getDetail(),
                entry.getTimestamp()
        );
    }

    @Transactional(readOnly = true)
    public List<AuditAction> distinctActions(Long effectiveUserId) {
        return effectiveUserId == null
                ? auditLogRepository.findDistinctActions()
                : auditLogRepository.findDistinctActionsForUser(effectiveUserId);
    }

    /**
     * Streams the filtered trail as CSV, in pages, so an export of tens of
     * thousands of rows does not first materialise them all in heap.
     */
    @Transactional(readOnly = true)
    public void writeCsv(AuditQuery query, Long effectiveUserId, Sort sort, OutputStream outputStream)
            throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.write("id,timestamp,userId,username,email,actorEmail,action,success,ipAddress,userAgent,failureReason,detail\n");

        int written = 0;
        int pageNumber = 0;
        while (written < EXPORT_MAX_ROWS) {
            Page<AuditLog> page = auditLogRepository.findAll(
                    query.toSpecification(effectiveUserId),
                    PageRequest.of(pageNumber, EXPORT_CHUNK, sort));
            if (page.isEmpty()) {
                break;
            }
            for (AuditLogResponse row : enrich(page.getContent())) {
                writer.write(toCsvLine(row));
                written++;
                if (written >= EXPORT_MAX_ROWS) {
                    break;
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageNumber++;
        }

        writer.flush();
        log.info("Audit CSV export complete — rows={}, scope={}", written,
                effectiveUserId == null ? "all users" : "userId=" + effectiveUserId);
    }

    private static String toCsvLine(AuditLogResponse row) {
        return String.join(",",
                csv(row.id()),
                csv(row.timestamp()),
                csv(row.userId()),
                csv(row.username()),
                csv(row.email()),
                csv(row.actorEmail()),
                csv(row.action()),
                csv(row.success()),
                csv(row.ipAddress()),
                csv(row.userAgent()),
                csv(row.failureReason()),
                csv(row.detail())) + "\n";
    }

    /**
     * Quotes a value for CSV, and neutralises spreadsheet formula injection.
     *
     * <p>A user agent is attacker-controlled text. A cell beginning
     * {@code =} or {@code +} is executed as a formula the moment an operator
     * opens the export in Excel, so such cells are prefixed with an
     * apostrophe and rendered inert.
     */
    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }

    // --- paging and sorting -------------------------------------------------

    /**
     * Turns the request's paging parameters into a {@link Pageable}, clamping
     * anything hostile. A negative page or a size of one million is corrected
     * rather than rejected — the caller gets data, not an error page.
     */
    public Pageable toPageable(Integer page, Integer size, String sort) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, toSort(sort));
    }

    /**
     * Parses {@code property,direction} against the allow-list, falling back
     * to newest-first.
     */
    public Sort toSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, DEFAULT_SORT_PROPERTY);
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!SORTABLE.contains(property)) {
            log.debug("Ignoring unsupported audit sort property '{}'", property);
            property = DEFAULT_SORT_PROPERTY;
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
            direction = Sort.Direction.ASC;
        }
        if ("id".equals(property)) {
            return Sort.by(direction, "id");
        }
        // Tie-break on id so pages are stable when many rows share a timestamp;
        // without it, row 20 of page 1 can reappear as row 1 of page 2.
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}

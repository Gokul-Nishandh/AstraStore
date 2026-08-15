package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.object.ObjectResponse;
import com.astrastore.metadata.service.ObjectService;
import com.astrastore.metadata.service.StarService;
import com.astrastore.metadata.web.ObjectResponseAssembler;
import com.astrastore.metadata.web.Pageables;
import com.astrastore.shared.security.AstraPrincipal;
import com.astrastore.shared.security.CurrentUser;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Starring, per user and per object.
 *
 * <p>Both mutations answer 204 whether or not they changed anything: the UI
 * toggle can fire twice, and two tabs can disagree, so "make it starred" is a
 * more useful contract than "create a star".
 *
 * <p>{@code GET /api/v1/starred} returns the same {@code ObjectResponse} shape
 * as every other listing so the dashboard reuses one table component.
 */
@RestController
@RequiredArgsConstructor
@Validated
public class StarController {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final StarService starService;
    private final ObjectService objectService;
    private final ObjectResponseAssembler assembler;

    /** Stars an object the caller owns. Idempotent. */
    @PutMapping("/api/v1/objects/{objectId}/star")
    public ResponseEntity<Void> star(@PathVariable UUID objectId) {
        AstraPrincipal principal = CurrentUser.require();
        CurrentUser.assertCanWrite();

        starService.star(objectId, principal);

        return ResponseEntity.noContent().build();
    }

    /** Removes the caller's star. Idempotent. */
    @DeleteMapping("/api/v1/objects/{objectId}/star")
    public ResponseEntity<Void> unstar(@PathVariable UUID objectId) {
        AstraPrincipal principal = CurrentUser.require();
        CurrentUser.assertCanWrite();

        starService.unstar(objectId, principal);

        return ResponseEntity.noContent().build();
    }

    /**
     * The caller's starred objects. Trashed objects never appear here even if a
     * star still points at them.
     */
    @GetMapping("/api/v1/starred")
    public ResponseEntity<Page<ObjectResponse>> listStarred(
            @RequestParam(required = false) @Size(max = 1024) String search,
            @PageableDefault(size = 50) Pageable pageable) {

        AstraPrincipal principal = CurrentUser.require();

        Page<ObjectResponse> starred = assembler.toPage(
                objectService.listStarredForUser(
                        principal.userId(),
                        Pageables.normalizeSearch(search),
                        Pageables.sanitize(pageable, Pageables.OBJECT_SORTS, DEFAULT_SORT)),
                principal.userId());

        return ResponseEntity.ok(starred);
    }
}

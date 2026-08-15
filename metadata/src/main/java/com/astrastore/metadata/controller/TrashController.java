package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.object.ObjectResponse;
import com.astrastore.metadata.service.ObjectService;
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

import java.util.Map;

/**
 * The caller's trash: objects soft-deleted by
 * {@code DELETE /api/v1/objects/{objectId}}.
 *
 * <p>Restore and permanent-delete of a single object live on
 * {@link ObjectController} alongside the object itself; this controller covers
 * the trash as a whole.
 */
@RestController
@RequestMapping("/api/v1/trash")
@RequiredArgsConstructor
@Validated
public class TrashController {

    /** Newest deletions first — the row a user is looking for is the one they just deleted. */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "deletedAt");

    private final ObjectService objectService;
    private final ObjectResponseAssembler assembler;

    /** Soft-deleted objects, each carrying its {@code deletedAt}. */
    @GetMapping
    public ResponseEntity<Page<ObjectResponse>> listTrash(
            @RequestParam(required = false) @Size(max = 1024) String search,
            @PageableDefault(size = 50) Pageable pageable) {

        AstraPrincipal principal = CurrentUser.require();

        Page<ObjectResponse> trash = assembler.toPage(
                objectService.listTrashForUser(
                        principal.userId(),
                        Pageables.normalizeSearch(search),
                        Pageables.sanitize(pageable, Pageables.OBJECT_SORTS, DEFAULT_SORT)),
                principal.userId());

        return ResponseEntity.ok(trash);
    }

    /**
     * Permanently deletes everything in the caller's trash and publishes the
     * chunk-cleanup events. Only the caller's own objects are touched.
     *
     * @return {@code {"deleted": n}}
     */
    @PostMapping("/empty")
    public ResponseEntity<Map<String, Integer>> emptyTrash() {
        AstraPrincipal principal = CurrentUser.require();
        CurrentUser.assertCanWrite();

        int deleted = objectService.emptyTrashForUser(principal.userId());

        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}

package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.object.ObjectResponse;
import com.astrastore.metadata.service.ObjectService;
import com.astrastore.metadata.web.ObjectResponseAssembler;
import com.astrastore.shared.security.AstraPrincipal;
import com.astrastore.shared.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Objects, scoped to the authenticated caller.
 *
 * <p>{@code GET /api/v1/objects/{objectId}} used to return any object to
 * anyone who could guess an id. Every handler here now resolves the record
 * through {@link ObjectService}'s owner-scoped methods, which answer NOT_FOUND
 * rather than FORBIDDEN for another account's object.
 */
@RestController
@RequestMapping("/api/v1/objects")
@RequiredArgsConstructor
@Validated
public class ObjectController {

    private static final int DEFAULT_RECENT_LIMIT = 10;

    private final ObjectService objectService;
    private final ObjectResponseAssembler assembler;

    /**
     * The caller's most recently created objects across every bucket.
     *
     * <p>Mapped above {@code /{objectId}} intentionally — "recent" is not a
     * UUID, so the literal path wins and the two never collide.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<ObjectResponse>> recentObjects(
            @RequestParam(defaultValue = "" + DEFAULT_RECENT_LIMIT) @Min(1) @Max(100) int limit) {

        AstraPrincipal principal = CurrentUser.require();

        return ResponseEntity.ok(assembler.toList(
                objectService.listRecentForUser(principal.userId(), limit),
                principal.userId()));
    }

    @GetMapping("/{objectId}")
    public ResponseEntity<ObjectResponse> getObject(@PathVariable UUID objectId) {
        AstraPrincipal principal = CurrentUser.require();

        return ResponseEntity.ok(assembler.toResponse(
                objectService.getObjectForUser(objectId, principal), principal.userId()));
    }

    /** Moves the object to the trash. Recoverable via {@code /restore}. */
    @DeleteMapping("/{objectId}")
    public ResponseEntity<Void> deleteObject(@PathVariable UUID objectId) {
        AstraPrincipal principal = CurrentUser.require();
        CurrentUser.assertCanWrite();

        objectService.softDeleteForUser(objectId, principal);

        return ResponseEntity.noContent().build();
    }

    /** Returns a trashed object to ACTIVE. */
    @PostMapping("/{objectId}/restore")
    public ResponseEntity<ObjectResponse> restoreObject(@PathVariable UUID objectId) {
        AstraPrincipal principal = CurrentUser.require();
        CurrentUser.assertCanWrite();

        return ResponseEntity.ok(assembler.toResponse(
                objectService.restoreForUser(objectId, principal), principal.userId()));
    }

    /**
     * Removes the metadata row for good and publishes chunk-cleanup events.
     * There is no undo.
     */
    @DeleteMapping("/{objectId}/permanent")
    public ResponseEntity<Void> permanentlyDeleteObject(@PathVariable UUID objectId) {
        AstraPrincipal principal = CurrentUser.require();
        CurrentUser.assertCanWrite();

        objectService.permanentlyDeleteForUser(objectId, principal);

        return ResponseEntity.noContent().build();
    }
}

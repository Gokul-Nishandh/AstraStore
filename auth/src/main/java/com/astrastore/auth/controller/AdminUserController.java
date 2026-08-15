/**
 * Administration of other people's accounts.
 *
 * <p>The class-level {@code @PreAuthorize} is the only authorisation check
 * written here. The rules that keep the deployment administrable — no
 * self-demotion, no removing the last administrator — deliberately live in
 * {@link UserAdminService} and not in this controller, so a caller who finds
 * another route to the service still hits them.
 */
package com.astrastore.auth.controller;

import com.astrastore.auth.dto.AdminUserResponse;
import com.astrastore.auth.dto.PageResponse;
import com.astrastore.auth.dto.UpdateRolesRequest;
import com.astrastore.auth.dto.UpdateStatusRequest;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.security.CallerContext;
import com.astrastore.auth.security.Roles;
import com.astrastore.auth.service.AuditLogService;
import com.astrastore.auth.service.UserAdminService;
import com.astrastore.shared.security.AstraPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final AuditLogService auditLogService;
    private final CallerContext callerContext;

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            HttpServletRequest httpRequest) {

        User actor = callerContext.requireUser();
        PageResponse<AdminUserResponse> result =
                userAdminService.list(search, role, userAdminService.toPageable(page, size, sort));

        auditLogService.record(actor.getId(), actor.getEmail(), AuditAction.ADMIN_USER_LIST_VIEWED,
                httpRequest, true, null,
                "Listed users — matched " + result.totalElements()
                        + (search == null || search.isBlank() ? "" : ", search='" + search + "'")
                        + (role == null || role.isBlank() ? "" : ", role=" + role));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/users/{id}/roles")
    public ResponseEntity<AdminUserResponse> updateRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRolesRequest request,
            HttpServletRequest httpRequest) {

        callerContext.requireInteractiveLogin("Changing roles");
        User actor = callerContext.requireUser();
        return ResponseEntity.ok(
                userAdminService.updateRoles(actor, id, request.roles(), httpRequest));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<AdminUserResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            HttpServletRequest httpRequest) {

        callerContext.requireInteractiveLogin("Enabling or disabling an account");
        User actor = callerContext.requireUser();
        return ResponseEntity.ok(userAdminService.updateStatus(
                actor, id, request.enabled(), request.reason(), httpRequest));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        callerContext.requireInteractiveLogin("Deleting an account");
        User actor = callerContext.requireUser();
        userAdminService.deleteUser(actor, id, httpRequest);
        return ResponseEntity.noContent().build();
    }

    /**
     * The role vocabulary, for the console's role picker.
     *
     * <p>Ordered most-privileged first rather than alphabetically, and served
     * from {@link Roles} rather than hardcoded in the client, so adding a
     * role to the platform does not need a frontend release.
     */
    @GetMapping("/roles")
    public ResponseEntity<List<String>> assignableRoles() {
        return ResponseEntity.ok(
                AstraPrincipal.ALL_ROLES.stream().filter(Roles.all()::contains).toList());
    }
}

/**
 * Body of {@code PATCH /api/auth/admin/users/{id}/roles}.
 *
 * <p>The set replaces the user's roles wholesale rather than adding to them,
 * so a console that renders checkboxes can send exactly what it displays and
 * never has to compute a diff. Membership of the role vocabulary is checked
 * in {@code Roles.normalise}; the guard rails that stop the deployment
 * becoming unadministrable live in the service and cannot be bypassed by
 * crafting a request.
 */
package com.astrastore.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateRolesRequest(

        @NotEmpty(message = "At least one role is required")
        @Size(max = 8, message = "Too many roles supplied")
        Set<@Size(min = 2, max = 32, message = "Role name is out of range") String> roles
) {
}

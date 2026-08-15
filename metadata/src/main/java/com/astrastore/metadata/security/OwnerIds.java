package com.astrastore.metadata.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Bridges the auth service's numeric user id to the UUID {@code owner_id}
 * column buckets have always been keyed on.
 *
 * <p>The real owner key is {@code Bucket.ownerUserId} (a {@code Long}, exactly
 * the {@code AstraPrincipal.userId}) and every isolation query uses it. The
 * legacy UUID column is retained because the {@code (owner_id, name)} unique
 * constraint and the frontend's {@code Bucket.ownerId} field both depend on
 * it, and {@code ddl-auto=update} cannot retype a column. Deriving it
 * deterministically from the user id keeps "one bucket name per user" working
 * without a migration.
 */
public final class OwnerIds {

    private static final String NAMESPACE = "astrastore-user:";

    private OwnerIds() {}

    /** Stable UUID for a numeric user id. Never null for a non-null input. */
    public static UUID forUser(Long userId) {
        if (userId == null) return null;
        return UUID.nameUUIDFromBytes((NAMESPACE + userId).getBytes(StandardCharsets.UTF_8));
    }
}

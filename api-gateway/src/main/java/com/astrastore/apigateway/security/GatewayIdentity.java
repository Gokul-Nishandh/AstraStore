package com.astrastore.apigateway.security;

import com.astrastore.shared.security.AstraPrincipal;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The caller as the edge resolved them — from a verified access token or from
 * an API key exchanged with the auth service.
 *
 * <p>Never carries the credential itself. {@code rateLimitSubject} is a
 * non-reversible label safe to use as a Redis key.
 */
public record GatewayIdentity(
        Long userId,
        String username,
        String email,
        Set<String> roles,
        boolean viaApiKey,
        String rateLimitSubject
) {

    public GatewayIdentity {
        roles = roles == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(roles));
    }

    public static GatewayIdentity fromToken(AstraPrincipal principal) {
        return new GatewayIdentity(
                principal.userId(),
                principal.username(),
                principal.email(),
                principal.roles(),
                principal.viaApiKey(),
                "user:" + principal.userId());
    }

    public String rolesHeaderValue() {
        return String.join(",", roles);
    }
}

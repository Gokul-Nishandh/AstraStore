package com.astrastore.shared.security;

import java.util.List;

/**
 * Identity headers the gateway sets on proxied requests.
 *
 * <p>These are informational — for logging and tracing — and are never a
 * substitute for verifying the token. Any inbound request carrying them is
 * stripped at the edge so a client can never assert its own identity.
 */
public final class AstraHeaders {

    public static final String USER_ID = "X-Astra-User-Id";
    public static final String USERNAME = "X-Astra-Username";
    public static final String ROLES = "X-Astra-Roles";
    public static final String REQUEST_ID = "X-Astra-Request-Id";

    /**
     * Carries the shared secret on the {@code /internal/**} service-to-service
     * surface, where there is no user identity to verify. Both the services
     * that present it and the filter that checks it read this constant, so the
     * name cannot drift between the two sides.
     *
     * <p>Unlike the identity headers above, this one <em>is</em> the
     * credential — never log it, and never let it out of the internal network.
     */
    public static final String SERVICE_TOKEN = "X-Astra-Service-Token";

    /** Headers the gateway must strip from every inbound client request. */
    public static final List<String> CLIENT_FORBIDDEN =
            List.of(USER_ID, USERNAME, ROLES);

    private AstraHeaders() {}
}

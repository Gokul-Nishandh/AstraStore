package com.astrastore.metadata.security;

import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards the service-to-service surface ({@code /internal/v1/**}).
 *
 * <p>Upload, download and replication call this service without a user token —
 * they act on behalf of a request that has already been authorised at the
 * gateway. They cannot present a JWT, so the internal path is gated on a
 * shared service token instead of user identity, and never applies ownership
 * rules.
 *
 * <p>Two modes, chosen by configuration:
 * <ul>
 *   <li><b>token configured</b> — every internal request must carry
 *       {@link AstraHeaders#SERVICE_TOKEN} with a matching value, or it is
 *       rejected with 401 in the standard {@link ApiError} envelope.</li>
 *   <li><b>token blank</b> — the internal path stays open. Reachable only
 *       under a development or test profile: elsewhere the service refuses to
 *       start rather than construct this filter unenforced (see
 *       {@code com.astrastore.shared.security.InternalServiceToken}). A warning
 *       is logged on every internal request (rate-limited to once per 1000).</li>
 * </ul>
 *
 * <p>The public {@code /api/v1/**} surface is never reachable through this
 * filter; it always requires a user JWT and always enforces ownership.
 */
@Slf4j
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    private final byte[] expectedToken;
    private final boolean enforced;

    private long openModeRequests;

    public InternalServiceTokenFilter(String serviceToken) {
        this.enforced = serviceToken != null && !serviceToken.isBlank();
        this.expectedToken = enforced ? serviceToken.trim().getBytes(StandardCharsets.UTF_8) : new byte[0];

        if (enforced) {
            log.info("Internal endpoints (/internal/v1/**) require the {} header.", AstraHeaders.SERVICE_TOKEN);
        } else {
            log.warn("SECURITY: /internal/v1/** is UNAUTHENTICATED because astrastore.internal.service-token "
                    + "is not set. Set ASTRA_INTERNAL_TOKEN on metadata, upload, download and replication "
                    + "to close this gap.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!enforced) {
            if (openModeRequests++ % 1000 == 0) {
                log.warn("SECURITY: serving {} {} with no service token configured.",
                        request.getMethod(), request.getRequestURI());
            }
            chain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(AstraHeaders.SERVICE_TOKEN);
        if (presented == null || !MessageDigest.isEqual(
                presented.trim().getBytes(StandardCharsets.UTF_8), expectedToken)) {
            log.warn("Rejected internal call to {} {} — missing or invalid service token.",
                    request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getOutputStream(),
                ApiError.of(ApiError.UNAUTHENTICATED,
                        "A valid service token is required for internal endpoints."));
    }
}

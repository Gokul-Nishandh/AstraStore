package com.astrastore.auth.security;

import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraHeaders;
import com.astrastore.shared.security.InternalServiceToken;
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
 * Guards auth's service-to-service surface ({@code /internal/**}).
 *
 * <p>Only one endpoint lives there — the API-key verification the gateway
 * calls — but it resolves a raw key to a full identity, so an unauthenticated
 * caller could use it as an oracle to test stolen keys. It is gated on the
 * same shared service token the other services use.
 *
 * <p>Applied ahead of the JWT filter and paired with a {@code permitAll} rule
 * for {@code /internal/**} in the security chain: the gateway presents a key,
 * not a bearer token, so requiring a JWT here would make the endpoint
 * impossible to call for its only purpose.
 */
@Slf4j
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final byte[] expectedToken;
    private final boolean enforced;

    public InternalServiceTokenFilter(InternalServiceToken serviceToken) {
        String value = serviceToken == null ? "" : serviceToken.value();
        this.enforced = value != null && !value.isBlank();
        this.expectedToken = enforced ? value.trim().getBytes(StandardCharsets.UTF_8) : new byte[0];

        if (!enforced) {
            log.warn("SECURITY: /internal/** on the auth service is UNAUTHENTICATED. "
                    + "Permitted only under a development profile.");
        }
    }

    /** Everything outside {@code /internal/} is somebody else's problem. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!enforced) {
            chain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(AstraHeaders.SERVICE_TOKEN);
        if (presented == null || !MessageDigest.isEqual(
                presented.trim().getBytes(StandardCharsets.UTF_8), expectedToken)) {
            log.warn("Rejected internal call to {} {} — missing or invalid service token.",
                    request.getMethod(), request.getRequestURI());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            MAPPER.writeValue(response.getOutputStream(),
                    ApiError.of(ApiError.UNAUTHENTICATED,
                            "A valid service token is required for internal endpoints."));
            return;
        }

        chain.doFilter(request, response);
    }
}

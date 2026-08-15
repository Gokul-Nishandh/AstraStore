package com.astrastore.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Populates the security context from a Bearer access token.
 *
 * <p>Installed by {@link AstraResourceServerConfig} in every service that
 * holds user data. A request without a valid token simply arrives
 * unauthenticated; rejecting it is the authorisation rules' job, not this
 * filter's, so that public endpoints keep working.
 */
public class AstraJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtTokenVerifier verifier;

    public AstraJwtAuthenticationFilter(JwtTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Never let a client forge identity headers. The gateway strips these
        // on the way in; stripping again here means a service is safe even if
        // it is reached directly.
        if (request.getHeader(AstraHeaders.USER_ID) != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Header present but unauthenticated — ignore it entirely.
            logger.debug("Ignoring client-supplied identity header");
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extractToken(request)
                    .flatMap(verifier::verify)
                    .ifPresent(principal -> authenticate(principal, request));
        }

        chain.doFilter(request, response);
    }

    private void authenticate(AstraPrincipal principal, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) return Optional.empty();
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}

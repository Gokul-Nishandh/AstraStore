package com.astrastore.auth.security;

import com.astrastore.auth.entity.User;
import com.astrastore.auth.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates requests carrying an {@code X-API-Key} header.
 * Runs before the JWT filter so programmatic clients (SDKs, scripts)
 * can call any protected endpoint without a Bearer token.
 * If the header is absent or invalid, the chain continues unauthenticated
 * and the JWT filter gets a chance to authenticate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    Optional<User> user = apiKeyService.validateApiKey(apiKey);
                    if (user.isPresent() && !user.get().isEnabled()) {
                        // Disabling an account must also cut off its keys.
                        log.warn("Rejected API key for disabled account — user={}",
                                user.get().getUsername());
                    } else if (user.isPresent()) {
                        UserDetails userDetails = user.get();
                        // A marker subtype, so handlers can refuse account-level
                        // operations to a long-lived script credential.
                        ApiKeyAuthenticationToken authToken =
                                new ApiKeyAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.debug("API key authenticated — user={}", user.get().getUsername());
                    }
                } catch (Exception e) {
                    log.warn("API key validation failed: {}", e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
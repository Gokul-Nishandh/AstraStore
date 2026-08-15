/**
 * Central Spring Security configuration.
 *
 * Authentication strategy: stateless JWT.
 * Sessions are disabled; every request must carry a valid Bearer token.
 *
 * Endpoints:
 *   POST /api/auth/register        — public (new user registration)
 *   POST /api/auth/login           — public (returns JWT + refresh token)
 *   POST /api/auth/refresh         — public (exchanges refresh token for new JWT)
 *   POST /api/auth/logout          — authenticated (revokes refresh token)
 *   POST /api/auth/forgot-password — public (a caller who has lost their password has no token)
 *   POST /api/auth/reset-password  — public (the reset token is the credential)
 *   /api/auth/account/**           — authenticated (own profile, password, deletion)
 *   /api/auth/keys/**              — authenticated (API key CRUD)
 *   /api/auth/audit/**             — authenticated (scoped to own rows unless ADMIN)
 *   /api/auth/admin/**             — ADMIN only
 *   /actuator/health               — public (health checks)
 *   /actuator/**                   — public (metrics & prometheus)
 *   all other requests             — require any authenticated user
 *
 * Method security is enabled so controllers can carry @PreAuthorize; the
 * matcher for /api/auth/admin/** below repeats the ADMIN requirement at the
 * filter chain, which is where a missing annotation on a new handler would
 * otherwise go unnoticed.
 *
 * Note: UserDetailsService and PasswordEncoder are defined in
 * PersistenceConfig to avoid circular dependency issues.
 */
package com.astrastore.auth.config;

import com.astrastore.auth.security.ApiKeyAuthenticationFilter;
import com.astrastore.auth.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.astrastore.auth.security.InternalServiceTokenFilter;
import com.astrastore.shared.security.InternalServiceToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Lazy
    private final JwtAuthenticationFilter jwtAuthFilter;
    @Lazy
    private final ApiKeyAuthenticationFilter apiKeyAuthFilter;
    private final InternalServiceToken internalServiceToken;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final ApiErrorSecurityHandler apiErrorSecurityHandler;

    /**
     * Explicitly last so more specific chains can be added ahead of it.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (token in body, not header)
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                .requestMatchers("/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // Gated by InternalServiceTokenFilter, not by Spring: the
                // gateway calls this with an API key to resolve, and has no
                // bearer token to offer.
                .requestMatchers("/internal/**").permitAll()
                // Administration — also guarded by @PreAuthorize on the controller
                .requestMatchers("/api/auth/admin", "/api/auth/admin/**").hasRole("ADMIN")
                // Authenticated user endpoints (require Bearer token in header)
                .requestMatchers("/api/auth/account", "/api/auth/account/**").authenticated()
                .requestMatchers("/api/auth/keys", "/api/auth/keys/**").authenticated()
                .requestMatchers("/api/auth/audit", "/api/auth/audit/**").authenticated()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            // A rejection here never reaches a controller, so
            // GlobalExceptionHandler cannot shape it. Without these two the
            // caller gets an empty 403 or the container's HTML error page
            // instead of the ApiError envelope every other failure uses.
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(apiErrorSecurityHandler)
                .accessDeniedHandler(apiErrorSecurityHandler)
            )
            .authenticationProvider(authenticationProvider())
            // Order of these two calls is load-bearing, not stylistic.
            // addFilterBefore can only target a filter class the chain already
            // knows the position of, so the JWT filter has to be placed first
            // before the API key filter can be placed relative to it.
            // Reversed, the context fails to start with "does not have a
            // registered order".
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiKeyAuthFilter, JwtAuthenticationFilter.class)
            // Ahead of both: /internal/** is gated on the shared service
            // token, not on a user credential, and must be settled before
            // anything tries to authenticate a caller that has none.
            .addFilterBefore(new InternalServiceTokenFilter(internalServiceToken),
                    ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

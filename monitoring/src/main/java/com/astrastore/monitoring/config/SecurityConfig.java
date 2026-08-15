package com.astrastore.monitoring.config;

import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraJwtAuthenticationFilter;
import com.astrastore.shared.security.AstraPrincipal;
import com.astrastore.shared.security.AstraResourceServerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Security for the monitoring service.
 *
 * <p>Extends {@link AstraResourceServerConfig}, inheriting the platform
 * baseline: the shared verifier and JWT filter, stateless sessions, and
 * actuator health/info/prometheus left open so the gateway's probes — and
 * this service's own sweep — can reach them.
 *
 * <p>One chain is layered ahead of it. The read API is administrators only:
 * the incident ledger names which services are failing and for how long,
 * which is exactly the reconnaissance an attacker with an ordinary account
 * would want.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig extends AstraResourceServerConfig {

    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Bean
    @Order(1)
    public SecurityFilterChain monitoringApiFilterChain(HttpSecurity http,
                                                        AstraJwtAuthenticationFilter jwtFilter)
            throws Exception {

        http
            .securityMatcher("/api/v1/monitoring/**")
            // The token travels in a header and no cookie is issued, so there
            // is no cross-site request to forge.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .anyRequest().hasRole(AstraPrincipal.ROLE_ADMIN))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) ->
                            writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                                    ApiError.UNAUTHENTICATED, "Sign in to continue."))
                    .accessDeniedHandler((req, res, e) ->
                            writeError(res, HttpServletResponse.SC_FORBIDDEN,
                                    ApiError.FORBIDDEN, "Administrator access is required."))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Rejections use the same envelope as every other failure on this service. */
    private static void writeError(HttpServletResponse response, int status,
                                   String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ERROR_MAPPER.writeValue(response.getOutputStream(), ApiError.of(code, message));
    }
}

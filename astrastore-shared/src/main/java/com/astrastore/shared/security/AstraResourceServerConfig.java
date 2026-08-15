package com.astrastore.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * Baseline security for every data-owning service behind the gateway.
 *
 * <p>Import this from a service's own {@code @Configuration} to get:
 * stateless sessions, a JWT filter, actuator health left open for
 * container probes, and everything else requiring authentication.
 *
 * <p>Services that need extra rules (admin-only paths, service-to-service
 * endpoints) should define their own {@link SecurityFilterChain} instead and
 * reuse {@link #astraJwtFilter} — one chain per service, no surprises about
 * ordering.
 *
 * <p>Rejections are emitted as the same JSON envelope the rest of the
 * platform uses, so a browser never has to render a raw servlet error page.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AstraResourceServerConfig {

    @Bean
    public JwtTokenVerifier jwtTokenVerifier(@Value("${jwt.secret}") String secret) {
        return new JwtTokenVerifier(secret);
    }

    @Bean
    public AstraJwtAuthenticationFilter astraJwtFilter(JwtTokenVerifier verifier) {
        return new AstraJwtAuthenticationFilter(verifier);
    }

    @Bean
    public SecurityFilterChain astraFilterChain(HttpSecurity http,
                                                AstraJwtAuthenticationFilter jwtFilter) throws Exception {
        http
            // No cookies are used; the token travels in a header, so CSRF
            // does not apply and a token cannot be replayed cross-site.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Liveness and readiness only — never the full actuator
                // surface, which would leak configuration and metrics.
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                        writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHENTICATED", "Sign in to continue."))
                .accessDeniedHandler((req, res, e) ->
                        writeError(res, HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN", "You do not have access to this resource."))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeError(HttpServletResponse response,
                                   int status,
                                   String code,
                                   String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        new ObjectMapper().writeValue(response.getOutputStream(), Map.of(
                "code", code,
                "message", message
        ));
    }
}

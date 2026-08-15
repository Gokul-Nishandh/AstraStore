package com.astrastore.metadata.config;

import com.astrastore.metadata.security.InternalServiceTokenFilter;
import com.astrastore.shared.security.AstraResourceServerConfig;
import com.astrastore.shared.security.InternalServiceToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/**
 * Security for the metadata service.
 *
 * <p>Extends {@link AstraResourceServerConfig}, so it inherits the platform
 * baseline verbatim: the shared {@code JwtTokenVerifier}, the JWT filter, and
 * the {@code astraFilterChain} that leaves only actuator health/info/prometheus
 * open and requires an authenticated caller for everything else. Before this,
 * anyone who could reach port 8084 could read or delete any user's objects.
 *
 * <p>One extra chain is layered on top, ordered ahead of the inherited one:
 * {@code /internal/v1/**} is the service-to-service surface used by upload,
 * download and replication. Those callers hold no user token, so that path is
 * gated on a shared service token by
 * {@link InternalServiceTokenFilter} rather than on user identity — and it is
 * the only path in the service that skips ownership checks.
 *
 * <p>Authentication alone is not isolation. Every handler on {@code /api/v1/**}
 * additionally resolves the record's owner and compares it against the caller,
 * answering 404 (not 403) when they differ so that a probe cannot confirm
 * another account's object exists.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig extends AstraResourceServerConfig {

    /**
     * Resolving the token here rather than reading the raw property inline is
     * what makes a missing one fatal: outside a development profile this throws
     * and the context never refreshes, so a misconfigured deployment fails to
     * start instead of serving {@code /internal/**} to anyone who asks.
     */
    @Bean
    public InternalServiceToken internalServiceToken(
            @Value("${" + InternalServiceToken.PROPERTY + ":}") String configured,
            Environment environment) {

        return InternalServiceToken.resolve(configured, List.of(environment.getActiveProfiles()));
    }

    /**
     * Service-to-service chain. Ordered first so it claims {@code /internal/**}
     * before the inherited catch-all chain (which is unordered, therefore
     * lowest precedence) can demand a user token those callers do not have.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(
            HttpSecurity http,
            InternalServiceToken serviceToken)
            throws Exception {

        http
            .securityMatcher("/internal/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // The token filter below is the gate; Spring Security itself has no
            // principal to authorise here, so the rules stay out of its way.
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(new InternalServiceTokenFilter(serviceToken.value()),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

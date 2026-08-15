package com.astrastore.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Who may call the API from a browser.
 *
 * <p>Expressed as a {@link CorsWebFilter} rather than {@code
 * spring.cloud.gateway.globalcors} so the origins come from
 * {@link GatewayProperties} — one list, typed, overridable per environment —
 * and so preflights are answered before the routing and authentication
 * filters run rather than after.
 *
 * <p>In the shipped topology nginx serves the dashboard and the API on one
 * origin, so none of this is exercised by the dashboard itself. It exists for
 * the SDK callers and any separately hosted front end, which is exactly the
 * case where a wildcard would be a real hole.
 */
@Configuration
public class GatewayCorsConfig {

    /**
     * Headers the platform actually reads. {@code X-API-Key} is the
     * programmatic credential and {@code X-Astra-Request-Id} lets a caller
     * supply its own correlation id.
     */
    private static final List<String> ALLOWED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            HttpHeaders.CONTENT_LENGTH,
            HttpHeaders.IF_NONE_MATCH,
            HttpHeaders.RANGE,
            "X-API-Key",
            "X-Astra-Request-Id"
    );

    /**
     * Without these on the list a cross-origin caller cannot read them at
     * all: {@code Content-Disposition} carries the download filename and
     * {@code X-Astra-Request-Id} is what a user quotes when reporting a
     * failure.
     */
    private static final List<String> EXPOSED_HEADERS = List.of(
            HttpHeaders.CONTENT_DISPOSITION,
            HttpHeaders.CONTENT_LENGTH,
            HttpHeaders.ETAG,
            "Retry-After",
            "X-Astra-Request-Id"
    );

    private static final List<String> ALLOWED_METHODS = List.of(
            HttpMethod.GET.name(),
            HttpMethod.HEAD.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()
    );

    @Bean
    public CorsWebFilter corsWebFilter(GatewayProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(properties.getAllowedOrigins()));
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/health/**", configuration);
        return new CorsWebFilter(source);
    }
}

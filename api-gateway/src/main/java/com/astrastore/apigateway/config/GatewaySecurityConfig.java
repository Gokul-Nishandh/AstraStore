package com.astrastore.apigateway.config;

import com.astrastore.apigateway.error.AstraErrorWebExceptionHandler;
import com.astrastore.apigateway.error.DownstreamErrorNormalizationFilter;
import com.astrastore.apigateway.security.ApiKeyIdentityResolver;
import com.astrastore.apigateway.security.DownstreamTokenIssuer;
import com.astrastore.apigateway.security.EdgeAuthenticationFilter;
import com.astrastore.apigateway.security.InternalPathGuardFilter;
import com.astrastore.apigateway.security.RequestIdWebFilter;
import com.astrastore.shared.security.JwtTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires the edge.
 *
 * <p>Spring Cloud Gateway runs on WebFlux, so the servlet-based
 * {@code AstraResourceServerConfig} the other services use cannot be applied
 * here. {@link JwtTokenVerifier} is deliberately Spring-free, which lets the
 * same verification logic — the same signing key, the same refusal to accept
 * a refresh token as an access token — run inside a reactive filter.
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public JwtTokenVerifier jwtTokenVerifier(@Value("${jwt.secret}") String secret) {
        return new JwtTokenVerifier(secret);
    }

    @Bean
    public DownstreamTokenIssuer downstreamTokenIssuer(@Value("${jwt.secret}") String secret,
                                                       GatewayProperties properties) {
        return new DownstreamTokenIssuer(secret, properties.getDownstreamTokenTtl());
    }

    @Bean
    public WebClient authServiceWebClient(WebClient.Builder builder, GatewayProperties properties) {
        return builder.baseUrl(properties.getAuthServiceUrl()).build();
    }

    @Bean
    public ApiKeyIdentityResolver apiKeyIdentityResolver(
            WebClient authServiceWebClient,
            ObjectProvider<ReactiveStringRedisTemplate> redis,
            ObjectMapper objectMapper,
            GatewayProperties properties) {
        // Redis is a cache, not a dependency: if it is absent the gateway
        // simply asks the auth service every time.
        return new ApiKeyIdentityResolver(
                authServiceWebClient, redis.getIfAvailable(), objectMapper, properties);
    }

    @Bean
    public EdgeAuthenticationFilter edgeAuthenticationFilter(JwtTokenVerifier verifier,
                                                             ApiKeyIdentityResolver resolver,
                                                             DownstreamTokenIssuer issuer,
                                                             GatewayProperties properties) {
        return new EdgeAuthenticationFilter(verifier, resolver, issuer, properties);
    }

    @Bean
    public RequestIdWebFilter requestIdWebFilter() {
        return new RequestIdWebFilter();
    }

    @Bean
    public InternalPathGuardFilter internalPathGuardFilter() {
        return new InternalPathGuardFilter();
    }

    @Bean
    public DownstreamErrorNormalizationFilter downstreamErrorNormalizationFilter() {
        return new DownstreamErrorNormalizationFilter();
    }

    @Bean
    public AstraErrorWebExceptionHandler astraErrorWebExceptionHandler() {
        return new AstraErrorWebExceptionHandler();
    }
}

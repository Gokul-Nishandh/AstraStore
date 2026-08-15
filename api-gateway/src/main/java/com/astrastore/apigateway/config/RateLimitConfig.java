package com.astrastore.apigateway.config;

import com.astrastore.apigateway.security.EdgeAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Per-key rate limiting (NFR-006), backed by Redis so the limit holds across
 * gateway replicas rather than per-instance.
 *
 * <p>The subject is chosen by the edge filter: an API key first (that is the
 * unit NFR-006 names), then the user id, then the client IP for the public
 * routes where there is nobody to name yet. Anonymous traffic is therefore
 * limited too, which is what makes the login limiter useful.
 */
@Configuration
public class RateLimitConfig {

    /**
     * The subject a request is counted against. Never the credential itself —
     * {@code EdgeAuthenticationFilter} stores an opaque label, so raw API keys
     * never become Redis key material.
     */
    @Bean
    public KeyResolver astraRateLimitKeyResolver() {
        return exchange -> {
            Object subject = exchange.getAttribute(
                    EdgeAuthenticationFilter.RATE_LIMIT_SUBJECT_ATTRIBUTE);
            if (subject instanceof String s && !s.isBlank()) {
                return Mono.just(s);
            }
            return Mono.just("ip:" + EdgeAuthenticationFilter.clientIp(exchange));
        };
    }

    /** General API traffic. */
    @Bean
    @Primary
    public RedisRateLimiter defaultRateLimiter(
            @Value("${astrastore.gateway.rate-limit.default.replenish-rate:50}") int replenishRate,
            @Value("${astrastore.gateway.rate-limit.default.burst-capacity:100}") int burstCapacity,
            @Value("${astrastore.gateway.rate-limit.default.requested-tokens:1}") int requestedTokens) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }

    /**
     * Sign-in and password reset. Deliberately far tighter: these are the only
     * endpoints where guessing repeatedly is the attack, and the limiter keys
     * on client IP because an attacker trying a password has no identity yet.
     */
    @Bean
    public RedisRateLimiter authRateLimiter(
            @Value("${astrastore.gateway.rate-limit.auth.replenish-rate:1}") int replenishRate,
            @Value("${astrastore.gateway.rate-limit.auth.burst-capacity:10}") int burstCapacity,
            @Value("${astrastore.gateway.rate-limit.auth.requested-tokens:1}") int requestedTokens) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }

    /** Account creation — cheap for us, but not worth allowing in bulk. */
    @Bean
    public RedisRateLimiter registrationRateLimiter(
            @Value("${astrastore.gateway.rate-limit.registration.replenish-rate:1}") int replenishRate,
            @Value("${astrastore.gateway.rate-limit.registration.burst-capacity:5}") int burstCapacity,
            @Value("${astrastore.gateway.rate-limit.registration.requested-tokens:1}") int requestedTokens) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }

    /**
     * Object upload and download. Generous on count — a single request may run
     * for minutes — but still bounded so one key cannot open unlimited streams.
     */
    @Bean
    public RedisRateLimiter transferRateLimiter(
            @Value("${astrastore.gateway.rate-limit.transfer.replenish-rate:20}") int replenishRate,
            @Value("${astrastore.gateway.rate-limit.transfer.burst-capacity:40}") int burstCapacity,
            @Value("${astrastore.gateway.rate-limit.transfer.requested-tokens:1}") int requestedTokens) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }
}

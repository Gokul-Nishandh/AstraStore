package com.astrastore.apigateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit-breaker behaviour for the routes that name one.
 *
 * <p>The reason this is Java and not properties: Spring Cloud CircuitBreaker
 * defaults its {@code TimeLimiter} to one second. Left alone, that aborts
 * every object upload and every large download the moment the route gains a
 * breaker — the timeout is the thing most likely to be wrong here, so it is
 * set explicitly rather than inherited.
 */
@Configuration
public class GatewayResilienceConfig {

    /** Names must match the {@code CircuitBreaker} filters in the routes. */
    private static final String[] TRANSFER_BREAKERS = {
            "uploadCircuitBreaker", "downloadCircuitBreaker"
    };

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> astraCircuitBreakerCustomizer(
            @Value("${astrastore.gateway.circuit-breaker.request-timeout:20s}") Duration requestTimeout,
            @Value("${astrastore.gateway.circuit-breaker.transfer-timeout:10m}") Duration transferTimeout) {

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                // A service restarting will fail a handful of calls; opening on
                // the first two would make a rolling deploy look like an outage.
                .minimumNumberOfCalls(10)
                .failureRateThreshold(60f)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(circuitBreakerConfig)
                    .timeLimiterConfig(TimeLimiterConfig.custom()
                            .timeoutDuration(requestTimeout)
                            .build())
                    .build());

            // Object bytes stream for as long as the client's connection lasts;
            // a slow 4 GiB upload is not a failure.
            factory.configure(builder -> builder
                    .circuitBreakerConfig(circuitBreakerConfig)
                    .timeLimiterConfig(TimeLimiterConfig.custom()
                            .timeoutDuration(transferTimeout)
                            .build()), TRANSFER_BREAKERS);
        };
    }
}

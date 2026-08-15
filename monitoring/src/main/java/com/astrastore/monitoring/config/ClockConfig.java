package com.astrastore.monitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Time as an injected dependency.
 *
 * <p>Every figure this service publishes is a function of "now", so the tests
 * that pin those figures need to control it rather than sleep.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

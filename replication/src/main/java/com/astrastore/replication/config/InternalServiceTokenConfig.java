package com.astrastore.replication.config;

import com.astrastore.shared.security.InternalServiceToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * The credential replication presents on metadata's {@code /internal/v1/**}
 * surface.
 *
 * <p>Resolving it as a bean makes a missing token fatal at startup outside a
 * development profile. Replica bookkeeping is retried and swallowed on failure,
 * so an unauthenticated replication service would look healthy while every
 * replica update it made was being dropped.
 */
@Configuration
public class InternalServiceTokenConfig {

    @Bean
    public InternalServiceToken internalServiceToken(
            @Value("${" + InternalServiceToken.PROPERTY + ":}") String configured,
            Environment environment) {

        return InternalServiceToken.resolve(configured, List.of(environment.getActiveProfiles()));
    }
}

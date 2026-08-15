package com.astrastore.upload.config;

import com.astrastore.shared.security.InternalServiceToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * The credential upload presents on metadata's {@code /internal/v1/**} surface.
 *
 * <p>Resolving it as a bean makes a missing token fatal at startup outside a
 * development profile: without it every metadata write would be rejected, and
 * an upload service that cannot commit metadata is better dead than silently
 * failing halfway through each upload.
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

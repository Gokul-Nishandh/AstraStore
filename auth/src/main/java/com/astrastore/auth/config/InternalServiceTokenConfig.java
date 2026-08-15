package com.astrastore.auth.config;

import com.astrastore.shared.security.InternalServiceToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * The credential the gateway must present on auth's {@code /internal/**}
 * surface, where API keys are resolved to identities.
 *
 * <p>Resolved as a bean so a missing token is fatal at startup outside a
 * development profile. An auth service that served key verification
 * unauthenticated would let anyone test stolen keys against it.
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

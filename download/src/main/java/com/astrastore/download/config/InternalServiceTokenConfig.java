package com.astrastore.download.config;

import com.astrastore.shared.security.InternalServiceToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * The credential download presents on metadata's {@code /internal/v1/**}
 * surface.
 *
 * <p>Resolving it as a bean makes a missing token fatal at startup outside a
 * development profile: chunk locations come from that surface, so without the
 * token every download would fail at read time instead.
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

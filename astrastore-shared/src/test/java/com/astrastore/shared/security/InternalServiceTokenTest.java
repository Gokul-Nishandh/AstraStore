package com.astrastore.shared.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalServiceTokenTest {

    @Test
    void configuredToken_isEnforced() {
        InternalServiceToken token = InternalServiceToken.resolve("  s3cret  ", List.of());

        assertTrue(token.isConfigured());
        assertEquals("s3cret", token.value());
    }

    /**
     * The whole point of the class: a deployment with no profile set — which is
     * what a plain {@code docker compose up} produces — must not start with an
     * open internal surface.
     */
    @Test
    void blankToken_withoutDevelopmentProfile_refusesToStart() {
        assertThrows(IllegalStateException.class,
                () -> InternalServiceToken.resolve("", List.of()));
        assertThrows(IllegalStateException.class,
                () -> InternalServiceToken.resolve(null, List.of("prod")));
    }

    @Test
    void blankToken_underDevelopmentProfile_staysOpen() {
        assertFalse(InternalServiceToken.resolve("", "test").isConfigured());
        assertFalse(InternalServiceToken.resolve("", "local").isConfigured());
        assertFalse(InternalServiceToken.resolve(" ", "kafka", "dev").isConfigured());
    }

    /** The failure message has to be actionable without leaking the secret. */
    @Test
    void failureMessage_namesThePropertyAndEnvironmentVariable() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> InternalServiceToken.resolve("", List.of()));

        assertTrue(failure.getMessage().contains(InternalServiceToken.PROPERTY));
        assertTrue(failure.getMessage().contains("ASTRA_INTERNAL_TOKEN"));
    }
}

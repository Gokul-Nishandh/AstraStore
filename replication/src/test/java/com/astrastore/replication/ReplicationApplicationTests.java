package com.astrastore.replication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The {@code test} profile is load-bearing, not decoration: without an active
 * development profile {@code InternalServiceToken.resolve} refuses to start a
 * service that has no internal service token. That refusal is exactly the
 * behaviour a real deployment needs, so this test opts into the development
 * path rather than the guard being weakened to accommodate it.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReplicationApplicationTests {

    @Test
    void contextLoads() {
    }

}

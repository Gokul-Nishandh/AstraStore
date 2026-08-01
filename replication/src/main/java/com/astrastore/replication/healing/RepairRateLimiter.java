/**
 * Rate limiter for self-healing repairs to prevent network overload.
 * Uses Guava RateLimiter to drip-feed repairs at 2 chunks/second.
 * Prevents the system from overwhelming storage nodes during mass healing events.
 */
package com.astrastore.replication.healing;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RepairRateLimiter {

    private static final double REPAIRS_PER_SECOND = 2.0;
    private static final long PERMIT_ACQUIRE_TIMEOUT_SECONDS = 30;

    private final RateLimiter rateLimiter;

    public RepairRateLimiter() {
        this.rateLimiter = RateLimiter.create(REPAIRS_PER_SECOND);
        log.info("RepairRateLimiter initialized — rate={} repairs/sec", REPAIRS_PER_SECOND);
    }

    public void throttle(List<String> chunkIds) {
        log.info("Throttling repair batch — size={}, rate={}/sec", chunkIds.size(), REPAIRS_PER_SECOND);

        for (String chunkId : chunkIds) {
            boolean acquired = rateLimiter.tryAcquire(PERMIT_ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (acquired) {
                log.debug("Rate limit permit acquired — chunkId={}", chunkId);
            } else {
                log.error("Rate limit permit acquisition timed out — chunkId={}, timeout={}s",
                        chunkId, PERMIT_ACQUIRE_TIMEOUT_SECONDS);
            }
        }

        log.info("Throttle batch complete — processed={}", chunkIds.size());
    }
}

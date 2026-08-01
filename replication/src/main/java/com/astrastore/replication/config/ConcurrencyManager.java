package com.astrastore.replication.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Manages concurrent replication streams per storage node.
 * Uses Semaphore per node to cap active outbound streams and prevent I/O starvation.
 */
@Component
@Slf4j
public class ConcurrencyManager {

    private static final int MAX_PERMITS_PER_NODE = 10;

    private final ConcurrentHashMap<String, Semaphore> nodeLocks = new ConcurrentHashMap<>();

    /**
     * Returns the Semaphore for a given node, creating it if necessary.
     * Each node can have up to MAX_PERMITS_PER_NODE concurrent outbound replications.
     *
     * @param nodeIp the storage node IP:port
     * @return the Semaphore for this node
     */
    public Semaphore getLock(String nodeIp) {
        return nodeLocks.computeIfAbsent(nodeIp, key -> {
            log.debug("Created new semaphore for node — nodeIp={}, maxPermits={}", nodeIp, MAX_PERMITS_PER_NODE);
            return new Semaphore(MAX_PERMITS_PER_NODE);
        });
    }

    /**
     * Attempts to acquire a permit for a node.
     *
     * @param nodeIp the storage node IP:port
     * @return true if permit acquired, false if node is at capacity
     */
    public boolean tryAcquire(String nodeIp) {
        Semaphore lock = getLock(nodeIp);
        boolean acquired = lock.tryAcquire();
        if (acquired) {
            log.debug("Permit acquired — nodeIp={}, availablePermits={}", nodeIp, lock.availablePermits());
        } else {
            log.warn("Permit denied — nodeIp={}, at capacity", nodeIp);
        }
        return acquired;
    }

    /**
     * Releases a permit for a node.
     *
     * @param nodeIp the storage node IP:port
     */
    public void release(String nodeIp) {
        Semaphore lock = getLock(nodeIp);
        lock.release();
        log.debug("Permit released — nodeIp={}, availablePermits={}", nodeIp, lock.availablePermits());
    }

    /**
     * Returns the number of available permits for a node.
     *
     * @param nodeIp the storage node IP:port
     * @return available permits, or 0 if node not found
     */
    public int getAvailablePermits(String nodeIp) {
        Semaphore lock = nodeLocks.get(nodeIp);
        return lock != null ? lock.availablePermits() : 0;
    }
}

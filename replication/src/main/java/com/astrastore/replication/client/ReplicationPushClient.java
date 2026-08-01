package com.astrastore.replication.client;

import com.astrastore.shared.events.ReplicationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

/**
 * Client for sending replication push commands to primary storage nodes.
 * Uses exponential backoff to survive network drops and node overload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReplicationPushClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final double MULTIPLIER = 2.0;

    private final RestTemplateBuilder restTemplateBuilder;

    /**
     * Sends a push replication command to a primary node.
     * Retries up to 3 times with exponential backoff (1s, 2s, 4s) on failure.
     *
     * @param primaryNodeIp the primary storage node to call
     * @param command       the replication command with chunkId and targetNodeIp
     * @return true if replication succeeded (200 OK), false otherwise
     */
    public boolean sendPushCommand(String primaryNodeIp, ReplicationCommand command) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        RestTemplate restTemplate = restTemplateBuilder.build();
        restTemplate.setRequestFactory(factory);

        String url = primaryNodeIp + "/api/v1/replication/push";
        log.info("Sending push command — primary={}, chunkId={}, target={}",
                primaryNodeIp, command.chunkId(), command.targetNodeIp());

        int attempt = 0;
        long backoffMs = INITIAL_BACKOFF_MS;

        while (attempt < MAX_ATTEMPTS) {
            try {
                HttpEntity<ReplicationCommand> entity = new HttpEntity<>(command);
                ResponseEntity<Void> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        Void.class
                );

                boolean success = response.getStatusCode().is2xxSuccessful();
                log.info("Push command result — primary={}, chunkId={}, success={}",
                        primaryNodeIp, command.chunkId(), success);
                return success;

            } catch (ResourceAccessException | HttpServerErrorException e) {
                attempt++;
                if (attempt >= MAX_ATTEMPTS) {
                    log.error("Push command exhausted retries — primary={}, chunkId={}, error={}",
                            primaryNodeIp, command.chunkId(), e.getMessage());
                    return false;
                }
                log.warn("Push command failed (attempt {}/{}) — primary={}, chunkId={}, error={}, retrying in {}ms",
                        attempt, MAX_ATTEMPTS, primaryNodeIp, command.chunkId(), e.getMessage(), backoffMs);
                try {
                    TimeUnit.MILLISECONDS.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoffMs = (long) (backoffMs * MULTIPLIER);
            }
        }

        return false;
    }
}

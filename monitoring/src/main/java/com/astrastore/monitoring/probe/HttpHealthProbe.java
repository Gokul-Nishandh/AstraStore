package com.astrastore.monitoring.probe;

import com.astrastore.monitoring.config.MonitoringProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;

/**
 * Probes an actuator health endpoint over HTTP.
 *
 * <p>Both the connect and the request timeout are set from
 * {@code probe-timeout-ms}: a service that accepts the connection and then
 * stops responding is the case that would otherwise pin a sweep thread until
 * the JDK default gives up, which is never.
 */
@Component
@Slf4j
public class HttpHealthProbe implements HealthProbe {

    private static final int MAX_ERROR_LENGTH = 480;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MonitoringProperties properties;
    private final Clock clock;

    public HttpHealthProbe(MonitoringProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.probeTimeout())
                // A redirect to a login page is not health; treat it as the
                // failure it is instead of following it somewhere unrelated.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public ProbeOutcome probe(MonitoringProperties.Target target) {
        String url = target.healthUrl(properties.getHealthPath());
        long startedAt = clock.millis();
        long startNanos = System.nanoTime();

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(properties.probeTimeout())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int elapsed = elapsedMillis(startNanos);

            String reported = readStatus(response.body());
            boolean twoXx = response.statusCode() >= 200 && response.statusCode() < 300;
            // Actuator answers 503 with a body when a component is down, so the
            // status code alone is not the whole answer; neither is the body,
            // which some endpoints omit.
            boolean up = twoXx && (reported == null || "UP".equalsIgnoreCase(reported));

            String error = up ? null
                    : "HTTP " + response.statusCode() + (reported == null ? "" : " (" + reported + ")");

            return new ProbeOutcome(target.getId(), startedAt, up, response.statusCode(),
                    elapsed, reported, error);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(target, startedAt, elapsedMillis(startNanos), "Probe interrupted");
        } catch (Exception e) {
            log.debug("Probe failed for {} at {}", target.getId(), url, e);
            return failure(target, startedAt, elapsedMillis(startNanos), describe(e));
        }
    }

    private ProbeOutcome failure(MonitoringProperties.Target target, long startedAt,
                                 int elapsed, String error) {
        return new ProbeOutcome(target.getId(), startedAt, false, null, elapsed, null, error);
    }

    private static int elapsedMillis(long startNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    /**
     * The exception type carries the useful distinction (refused vs. timed out
     * vs. unresolvable); the message often carries only the URL back again.
     */
    private static String describe(Exception e) {
        String message = e.getMessage();
        String text = (message == null || message.isBlank())
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }

    private String readStatus(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode status = node.get("status");
            return status == null || status.isNull() ? null : status.asText();
        } catch (Exception e) {
            return null;
        }
    }
}

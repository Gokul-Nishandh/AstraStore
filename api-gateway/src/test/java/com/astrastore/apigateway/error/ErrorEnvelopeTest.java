package com.astrastore.apigateway.error;

import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four ways a failure can reach a browser, and the one shape it is
 * allowed to arrive in.
 *
 * <p>Every assertion here exists because the same failure once reached a user
 * as a bare status code or a Java class name.
 */
class ErrorEnvelopeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DefaultDataBufferFactory BUFFERS = new DefaultDataBufferFactory();

    /** Text that must never appear in anything the gateway writes out. */
    private static final String[] LEAKS = {
            "Exception", "at com.", "at org.", "metadata:8084", "172.18",
            "astrastore-metadata", "Whitelabel", "Internal Server Error"
    };

    // --- 1. A dead downstream, surfaced as a raw connection failure -------

    @Test
    void connectionFailureBecomesTheUnavailableEnvelope() {
        MockServerWebExchange exchange = exchange();
        Throwable failure = new RuntimeException("proxy failed",
                new ConnectException("Connection refused: metadata:8084/172.18.0.5:8084"));

        new AstraErrorWebExceptionHandler().handle(exchange, failure).block();

        JsonNode body = bodyOf(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertEnvelope(body, ApiError.SERVICE_UNAVAILABLE);
        assertThat(body.get("message").asText()).isEqualTo(ErrorResponses.UNAVAILABLE_MESSAGE);
        assertNoLeaks(exchange);
    }

    @Test
    void anUnexpectedFailureNeverNamesItsCause() {
        MockServerWebExchange exchange = exchange();

        new AstraErrorWebExceptionHandler()
                .handle(exchange, new IllegalStateException("column \"user_id\" does not exist"))
                .block();

        JsonNode body = bodyOf(exchange);
        assertEnvelope(body, ApiError.INTERNAL_ERROR);
        assertThat(body.get("message").asText()).doesNotContain("user_id");
        assertNoLeaks(exchange);
    }

    // --- 2. A path nothing routes ----------------------------------------

    @Test
    void unmatchedRouteBecomesTheNotFoundEnvelope() {
        MockServerWebExchange exchange = exchange();

        new AstraErrorWebExceptionHandler()
                .handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND))
                .block();

        JsonNode body = bodyOf(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertEnvelope(body, ApiError.NOT_FOUND);
    }

    @Test
    void theEnvelopeCarriesTheRequestIdTheClientSent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway.test/api/v1/nope")
                        .header(AstraHeaders.REQUEST_ID, "abcd1234-req"));

        new AstraErrorWebExceptionHandler()
                .handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND))
                .block();

        assertThat(bodyOf(exchange).get("requestId").asText()).isEqualTo("abcd1234-req");
    }

    // --- 3. A rate-limit rejection: a status and no body at all -----------

    @Test
    void rateLimitRejectionBecomesRateLimitedNotABare429() {
        MockServerWebExchange exchange = exchange();

        run(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().setComplete();
        });

        JsonNode body = bodyOf(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertEnvelope(body, ApiError.RATE_LIMITED);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
    }

    // --- 4. A downstream that answers with a framework error body ---------

    @Test
    void springDefaultErrorBodyIsReplaced() {
        MockServerWebExchange exchange = exchange();
        String springDefault = """
                {"timestamp":"2026-01-01T00:00:00.000+00:00","status":503,\
                "error":"Service Unavailable","path":"/api/v1/objects/abc"}""";

        run(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return ex.getResponse().writeWith(Mono.just(buffer(springDefault)));
        });

        JsonNode body = bodyOf(exchange);
        assertEnvelope(body, ApiError.SERVICE_UNAVAILABLE);
        assertThat(body.has("path")).isFalse();
        assertNoLeaks(exchange);
    }

    @Test
    void htmlErrorPageIsReplaced() {
        MockServerWebExchange exchange = exchange();

        run(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            ex.getResponse().getHeaders().setContentType(MediaType.TEXT_HTML);
            return ex.getResponse().writeWith(Mono.just(buffer(
                    "<html><body><h1>Whitelabel Error Page</h1>"
                            + "java.lang.NullPointerException at com.astrastore.metadata"
                            + "</body></html>")));
        });

        assertEnvelope(bodyOf(exchange), ApiError.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertNoLeaks(exchange);
    }

    /** The whole point of inspecting rather than rewriting blindly. */
    @Test
    void aDownstreamsOwnEnvelopeSurvivesIntact() {
        MockServerWebExchange exchange = exchange();
        String downstream = """
                {"code":"OBJECT_NOT_FOUND","message":"That file is no longer in this bucket.",\
                "requestId":"r-1","timestamp":"2026-01-01T00:00:00Z"}""";

        run(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return ex.getResponse().writeWith(Mono.just(buffer(downstream)));
        });

        JsonNode body = bodyOf(exchange);
        assertThat(body.get("code").asText()).isEqualTo("OBJECT_NOT_FOUND");
        assertThat(body.get("message").asText()).isEqualTo("That file is no longer in this bucket.");
    }

    @Test
    void successfulResponsesAreNotTouched() {
        MockServerWebExchange exchange = exchange();

        run(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return ex.getResponse().writeWith(Mono.just(buffer("the object bytes")));
        });

        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("the object bytes");
    }

    // --- Helpers ---------------------------------------------------------

    private static void run(MockServerWebExchange exchange, GatewayFilterChain chain) {
        new DownstreamErrorNormalizationFilter().filter(exchange, chain).block();
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("http://gateway.test/api/v1/objects/abc"));
    }

    private static DataBuffer buffer(String text) {
        return BUFFERS.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode bodyOf(MockServerWebExchange exchange) {
        String raw = exchange.getResponse().getBodyAsString().block();
        assertThat(raw).as("no body written").isNotNull();
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new AssertionError("Response body was not JSON: " + raw, e);
        }
    }

    private static void assertEnvelope(JsonNode body, String expectedCode) {
        assertThat(body.get("code").asText()).isEqualTo(expectedCode);
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.has("requestId")).isTrue();
        assertThat(body.has("timestamp")).isTrue();
    }

    private static void assertNoLeaks(MockServerWebExchange exchange) {
        String raw = exchange.getResponse().getBodyAsString().block();
        assertThat(raw).isNotNull();
        for (String leak : LEAKS) {
            assertThat(raw).doesNotContain(leak);
        }
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.SERVER)).isNull();
    }
}

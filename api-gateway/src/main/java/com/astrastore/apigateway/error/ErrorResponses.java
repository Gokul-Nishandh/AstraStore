package com.astrastore.apigateway.error;

import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Writes the one error shape the platform returns, {@link ApiError}.
 *
 * <p>Everything a client sees on a failure comes through here, which is how
 * "503" and Java exception names stop reaching the browser.
 */
public final class ErrorResponses {

    /** Human-readable text for the two failure modes users actually hit. */
    public static final String UNAVAILABLE_MESSAGE =
            "AstraStore is temporarily unavailable. Please try again in a moment.";
    public static final String INTERNAL_MESSAGE =
            "Something went wrong on our side. The problem has been logged — please try again.";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    private ErrorResponses() {}

    public static Mono<Void> write(ServerWebExchange exchange,
                                   HttpStatus status,
                                   String code,
                                   String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(new IllegalStateException("Response already committed"));
        }
        response.setStatusCode(status);
        return writeBody(exchange, status, code, message);
    }

    /**
     * Serialises the envelope onto an already-status-stamped response. Used by
     * the response decorator, which must not change a downstream's status.
     */
    public static Mono<Void> writeBody(ServerWebExchange exchange,
                                       HttpStatus status,
                                       String code,
                                       String message) {
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestId = requestId(exchange);
        if (requestId != null) {
            headers.set(AstraHeaders.REQUEST_ID, requestId);
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS && !headers.containsKey("Retry-After")) {
            headers.set("Retry-After", "1");
        }

        // A HEAD response must carry headers but no body.
        if (HttpMethod.HEAD.equals(exchange.getRequest().getMethod())) {
            headers.setContentLength(0);
            return response.setComplete();
        }

        byte[] bytes = serialise(new ApiError(code, message, requestId, java.time.Instant.now()));
        headers.setContentLength(bytes.length);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    public static String requestId(ServerWebExchange exchange) {
        String fromResponse = exchange.getResponse().getHeaders().getFirst(AstraHeaders.REQUEST_ID);
        if (fromResponse != null) return fromResponse;
        return exchange.getRequest().getHeaders().getFirst(AstraHeaders.REQUEST_ID);
    }

    private static byte[] serialise(ApiError error) {
        try {
            return MAPPER.writeValueAsBytes(error);
        } catch (Exception e) {
            // Last-resort envelope; still valid JSON, still no internals.
            return ("{\"code\":\"" + ApiError.INTERNAL_ERROR + "\",\"message\":\""
                    + INTERNAL_MESSAGE + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }
}

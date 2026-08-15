package com.astrastore.apigateway.error;

import com.astrastore.shared.api.ApiError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guarantees that no failure leaves the gateway as anything but an
 * {@link ApiError}.
 *
 * <p>Two things produce a bare status code with no usable body: a downstream
 * that answers 5xx with an HTML error page, and gateway filters — the rate
 * limiter above all — that set a status and complete the response with
 * nothing in it. Both used to reach the browser as a naked "503" or "429".
 *
 * <p>Successful responses are passed through untouched, so object downloads
 * still stream straight from the storage path without being buffered here.
 * A downstream error carrying the shared envelope is passed through as well —
 * rewriting it would discard a precise code like {@code OBJECT_NOT_FOUND} in
 * favour of a vague one. Being JSON is not enough to qualify: Spring's own
 * default error body is JSON too, and it is exactly the {@code {"status":503,
 * "error":"Service Unavailable","path":"/api/v1/..."}} shape the user was
 * complaining about. The presence of a {@code code} field is what separates
 * an AstraStore error from a framework one.
 */
public class DownstreamErrorNormalizationFilter implements GlobalFilter, Ordered {

    /** Must wrap the response before anything writes to it. */
    public static final int ORDER = -110;

    /**
     * Ceiling on an error body held in memory for inspection. Anything larger
     * is not an error message a person was meant to read, so it is discarded
     * rather than buffered.
     */
    static final int MAX_INSPECTED_ERROR_BYTES = 64 * 1024;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse decorated = new NormalizingResponse(exchange);
        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private static final class NormalizingResponse extends ServerHttpResponseDecorator {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final ServerWebExchange exchange;
        private final AtomicBoolean replaced = new AtomicBoolean(false);

        private NormalizingResponse(ServerWebExchange exchange) {
            super(exchange.getResponse());
            this.exchange = exchange;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            if (!isError()) {
                return super.writeWith(body);
            }
            if (!replaced.compareAndSet(false, true)) {
                return Mono.empty();
            }
            if (!isJson(getHeaders().getContentType())) {
                // Drain and release whatever the downstream produced — an HTML
                // error page or a stack trace, never something a user reads.
                return Flux.from(body)
                        .doOnNext(DataBufferUtils::release)
                        .then(Mono.defer(this::writeEnvelope));
            }
            // The emptiness that matters is "the downstream sent no body",
            // which join signals by completing without a value. It has to be
            // captured here, on the DataBuffer, rather than with
            // switchIfEmpty further down the chain: everything after the
            // flatMap is a Mono<Void>, which never emits, so a switchIfEmpty
            // there fires on every response and writes the envelope a second
            // time onto an already-committed one.
            return DataBufferUtils.join(Flux.from(body), MAX_INSPECTED_ERROR_BYTES)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(joined -> joined
                            .map(this::forwardIfEnvelope)
                            .orElseGet(this::writeEnvelope))
                    // An oversized or unreadable error body is not worth
                    // rescuing; the generic envelope says the same thing safely.
                    .onErrorResume(ignored -> writeEnvelope());
        }

        private Mono<Void> forwardIfEnvelope(DataBuffer joined) {
            byte[] bytes = new byte[joined.readableByteCount()];
            joined.read(bytes);
            DataBufferUtils.release(joined);

            if (!isAstraEnvelope(bytes)) {
                return writeEnvelope();
            }
            getHeaders().setContentLength(bytes.length);
            return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
        }

        /**
         * True only for the shared {@link ApiError} shape. A framework error
         * body is valid JSON and carries no {@code code}, which is what makes
         * this a usable test rather than a guess.
         */
        private static boolean isAstraEnvelope(byte[] bytes) {
            try {
                JsonNode root = MAPPER.readTree(bytes);
                JsonNode code = root.path("code");
                return code.isTextual() && !code.asText().isBlank();
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            if (!isError()) {
                return super.writeAndFlushWith(body);
            }
            return writeWith(Flux.from(body).flatMap(Flux::from));
        }

        @Override
        public Mono<Void> setComplete() {
            // The rate limiter and other short-circuiting filters land here:
            // a status, no body at all.
            if (isError() && replaced.compareAndSet(false, true)) {
                return writeEnvelope();
            }
            return super.setComplete();
        }

        private boolean isError() {
            HttpStatusCode status = getStatusCode();
            return status != null && status.isError();
        }

        private static boolean isJson(MediaType contentType) {
            if (contentType == null) return false;
            return MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                    || contentType.getSubtype().endsWith("+json");
        }

        private Mono<Void> writeEnvelope() {
            // Once the response is committed its headers are read-only and the
            // status is on the wire, so there is nothing left to replace. This
            // is the backstop for a downstream that fails partway through a
            // body it had already begun streaming.
            if (isCommitted()) {
                return Mono.empty();
            }

            HttpStatusCode statusCode = getStatusCode();
            HttpStatus status = statusCode == null
                    ? HttpStatus.INTERNAL_SERVER_ERROR
                    : HttpStatus.resolve(statusCode.value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

            HttpHeaders headers = getHeaders();
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.remove(HttpHeaders.TRANSFER_ENCODING);

            // Writes through the undecorated response — this decorator is
            // exactly what we are bypassing.
            return ErrorResponses.writeBody(exchange, status, codeFor(status), messageFor(status));
        }

        private static String codeFor(HttpStatus status) {
            return switch (status) {
                case UNAUTHORIZED -> ApiError.UNAUTHENTICATED;
                case FORBIDDEN -> ApiError.FORBIDDEN;
                case NOT_FOUND -> ApiError.NOT_FOUND;
                case CONFLICT -> ApiError.CONFLICT;
                case PAYLOAD_TOO_LARGE -> ApiError.PAYLOAD_TOO_LARGE;
                case TOO_MANY_REQUESTS -> ApiError.RATE_LIMITED;
                case BAD_REQUEST, UNPROCESSABLE_ENTITY -> ApiError.VALIDATION_FAILED;
                case SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT, BAD_GATEWAY -> ApiError.SERVICE_UNAVAILABLE;
                default -> status.is5xxServerError() ? ApiError.SERVICE_UNAVAILABLE
                        : ApiError.VALIDATION_FAILED;
            };
        }

        private static String messageFor(HttpStatus status) {
            return switch (status) {
                case UNAUTHORIZED -> "Sign in to continue.";
                case FORBIDDEN -> "You do not have access to this resource.";
                case NOT_FOUND -> "We could not find what you asked for.";
                case CONFLICT -> "That conflicts with something that already exists.";
                case PAYLOAD_TOO_LARGE -> "That upload is larger than the maximum allowed size.";
                case TOO_MANY_REQUESTS -> "Too many requests. Please slow down and try again shortly.";
                case BAD_REQUEST, UNPROCESSABLE_ENTITY -> "That request could not be processed as sent.";
                case METHOD_NOT_ALLOWED -> "That action is not supported on this address.";
                default -> status.is5xxServerError()
                        ? ErrorResponses.UNAVAILABLE_MESSAGE
                        : "That request could not be processed as sent.";
            };
        }
    }
}

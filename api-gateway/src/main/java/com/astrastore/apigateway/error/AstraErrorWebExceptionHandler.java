package com.astrastore.apigateway.error;

import com.astrastore.shared.api.ApiError;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * The last thing between a failure and the user's browser.
 *
 * <p>Nothing gets past this as a raw status code, a Java class name, an
 * internal hostname or a stack trace. The user complained about seeing bare
 * "503"s; a dead downstream now reads as "AstraStore is temporarily
 * unavailable" with a request id they can quote, while the actual cause is
 * logged once, server-side, against that same id.
 *
 * <p>Ordered ahead of Boot's {@code DefaultErrorWebExceptionHandler} (-1) so
 * the whitelabel error page is never reached.
 */
@Order(-2)
public class AstraErrorWebExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AstraErrorWebExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            // Too late to say anything useful; let it propagate so the
            // connection is torn down rather than half-answered.
            return Mono.error(ex);
        }

        Classification classification = classify(ex);
        String requestId = ErrorResponses.requestId(exchange);

        if (classification.logStackTrace()) {
            log.error("Unhandled gateway failure — requestId={}, path={}",
                    requestId, exchange.getRequest().getPath().value(), ex);
        } else {
            log.warn("Gateway returning {} — requestId={}, path={}, cause={}",
                    classification.status().value(), requestId,
                    exchange.getRequest().getPath().value(), summarise(ex));
        }

        return ErrorResponses.write(exchange, classification.status(),
                classification.code(), classification.message());
    }

    /**
     * Maps a throwable to what the user is told. Deliberately coarse: the
     * distinction between "connection refused" and "DNS failure" matters to
     * an operator reading logs, never to the person waiting on a page.
     */
    static Classification classify(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (isUnreachable(cause)) {
                return new Classification(HttpStatus.SERVICE_UNAVAILABLE,
                        ApiError.SERVICE_UNAVAILABLE, ErrorResponses.UNAVAILABLE_MESSAGE, false);
            }
            if (isCircuitOpen(cause)) {
                return new Classification(HttpStatus.SERVICE_UNAVAILABLE,
                        ApiError.SERVICE_UNAVAILABLE, ErrorResponses.UNAVAILABLE_MESSAGE, false);
            }
            if (cause instanceof ResponseStatusException rse) {
                return fromStatus(rse.getStatusCode());
            }
            if (cause instanceof ErrorResponseException ere) {
                return fromStatus(ere.getStatusCode());
            }
            if (isGatewayNotFound(cause)) {
                return new Classification(HttpStatus.SERVICE_UNAVAILABLE,
                        ApiError.SERVICE_UNAVAILABLE, ErrorResponses.UNAVAILABLE_MESSAGE, false);
            }
        }
        return new Classification(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiError.INTERNAL_ERROR, ErrorResponses.INTERNAL_MESSAGE, true);
    }

    private static boolean isUnreachable(Throwable cause) {
        return cause instanceof ConnectException
                || cause instanceof UnknownHostException
                || cause instanceof ConnectTimeoutException
                || cause instanceof ReadTimeoutException
                || cause instanceof TimeoutException
                || cause instanceof java.nio.channels.ClosedChannelException
                || (cause instanceof IOException io && isConnectionReset(io))
                || isPrematureClose(cause);
    }

    private static boolean isConnectionReset(IOException io) {
        String message = io.getMessage();
        return message != null && message.toLowerCase().contains("connection reset");
    }

    /** Reactor Netty's {@code PrematureCloseException} is package-scoped. */
    private static boolean isPrematureClose(Throwable cause) {
        return cause.getClass().getName().endsWith("PrematureCloseException");
    }

    /** Resilience4j is optional on the classpath; match by name, not type. */
    private static boolean isCircuitOpen(Throwable cause) {
        String name = cause.getClass().getName();
        return name.endsWith("CallNotPermittedException")
                || name.endsWith("NoFallbackAvailableException")
                || name.endsWith("RequestNotPermitted");
    }

    /** Spring Cloud Gateway signals "no instance to route to" this way. */
    private static boolean isGatewayNotFound(Throwable cause) {
        return cause.getClass().getName()
                .equals("org.springframework.cloud.gateway.support.NotFoundException");
    }

    private static Classification fromStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        return switch (status) {
            case NOT_FOUND -> new Classification(status, ApiError.NOT_FOUND,
                    "We could not find what you asked for.", false);
            case UNAUTHORIZED -> new Classification(status, ApiError.UNAUTHENTICATED,
                    "Sign in to continue.", false);
            case FORBIDDEN -> new Classification(status, ApiError.FORBIDDEN,
                    "You do not have access to this resource.", false);
            case TOO_MANY_REQUESTS -> new Classification(status, ApiError.RATE_LIMITED,
                    "Too many requests. Please slow down and try again shortly.", false);
            case PAYLOAD_TOO_LARGE -> new Classification(status, ApiError.PAYLOAD_TOO_LARGE,
                    "That upload is larger than the maximum allowed size.", false);
            case METHOD_NOT_ALLOWED -> new Classification(status, ApiError.VALIDATION_FAILED,
                    "That action is not supported on this address.", false);
            case BAD_REQUEST, UNPROCESSABLE_ENTITY -> new Classification(status,
                    ApiError.VALIDATION_FAILED, "That request could not be processed as sent.", false);
            case SERVICE_UNAVAILABLE, BAD_GATEWAY, GATEWAY_TIMEOUT -> new Classification(status,
                    ApiError.SERVICE_UNAVAILABLE, ErrorResponses.UNAVAILABLE_MESSAGE, false);
            default -> status.is5xxServerError()
                    ? new Classification(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.INTERNAL_ERROR,
                            ErrorResponses.INTERNAL_MESSAGE, true)
                    : new Classification(status, ApiError.VALIDATION_FAILED,
                            "That request could not be processed as sent.", false);
        };
    }

    /** Class name plus nothing else — messages routinely carry hostnames. */
    private static String summarise(Throwable ex) {
        return ex.getClass().getSimpleName();
    }

    record Classification(HttpStatus status, String code, String message, boolean logStackTrace) {}
}

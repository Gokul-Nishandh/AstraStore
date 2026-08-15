package com.astrastore.apigateway.error;

import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.time.Instant;

/**
 * Where a route lands when its circuit breaker is open or its call timed out.
 *
 * <p>Without this, an open circuit surfaces as a bare 503 from the gateway
 * itself. The dashboard gets the same envelope it gets for every other
 * failure, so it can show one message instead of special-casing the outage.
 */
@Configuration
public class FallbackRoutes {

    /** Internal-only path; routes point their {@code fallbackUri} here. */
    public static final String FALLBACK_PATH = "/__astra/unavailable";

    @Bean
    public RouterFunction<ServerResponse> unavailableFallbackRoute() {
        // Any method, not just GET — a failed upload lands here too, so the
        // predicate matches on path alone.
        return RouterFunctions.route(RequestPredicates.path(FALLBACK_PATH), request -> {
            String requestId = request.headers().firstHeader(AstraHeaders.REQUEST_ID);
            return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ApiError(
                            ApiError.SERVICE_UNAVAILABLE,
                            ErrorResponses.UNAVAILABLE_MESSAGE,
                            requestId,
                            Instant.now()));
        });
    }
}

package com.astrastore.apigateway.security;

import com.astrastore.apigateway.error.ErrorResponses;
import com.astrastore.shared.api.ApiError;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Refuses paths that exist for the platform's own use.
 *
 * <p>{@code /internal/**} is the service-to-service surface — API-key
 * verification, chunk index writes — and is reachable over the container
 * network without a user token. No route publishes it, so this is a second
 * lock rather than the only one, but "no route matches it today" is not a
 * property that survives someone adding a prefix route.
 *
 * <p>{@code /__astra/**} is the circuit-breaker fallback. A route reaches it
 * through {@code forward:}, which re-dispatches inside the handler and never
 * re-runs web filters — so anything arriving here came from outside, and the
 * answer is that the path does not exist.
 */
public class InternalPathGuardFilter implements WebFilter, Ordered {

    /** Immediately after the request id is assigned, before anything routes. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private static final List<String> BLOCKED_PREFIXES = List.of("/internal/", "/__astra/");

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (isBlocked(path)) {
            // 404, not 403: a caller who cannot use these should not learn
            // that they exist.
            return ErrorResponses.write(exchange, HttpStatus.NOT_FOUND,
                    ApiError.NOT_FOUND, "We could not find what you asked for.");
        }
        return chain.filter(exchange);
    }

    private static boolean isBlocked(String path) {
        return BLOCKED_PREFIXES.stream().anyMatch(prefix ->
                path.equals(prefix.substring(0, prefix.length() - 1)) || path.startsWith(prefix));
    }
}

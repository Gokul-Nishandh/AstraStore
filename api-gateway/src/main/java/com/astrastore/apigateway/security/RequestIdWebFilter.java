package com.astrastore.apigateway.security;

import com.astrastore.shared.security.AstraHeaders;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Stamps every request with a correlation id, before routing.
 *
 * <p>{@link EdgeAuthenticationFilter} does this too, but it is a
 * {@code GlobalFilter} and those run only once a route has matched. A request
 * for a path nothing routes — the case that produces a 404 — would otherwise
 * end up with an {@code ApiError} whose {@code requestId} is null, which is
 * precisely the failure a user is most likely to report.
 *
 * <p>The id is written onto the request as well as the response so the edge
 * filter re-resolves the same value rather than minting a second one.
 */
public class RequestIdWebFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = EdgeAuthenticationFilter.resolveRequestId(exchange.getRequest());
        exchange.getResponse().getHeaders().set(AstraHeaders.REQUEST_ID, requestId);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> headers.set(AstraHeaders.REQUEST_ID, requestId))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }
}

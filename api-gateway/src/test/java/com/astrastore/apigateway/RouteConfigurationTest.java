package com.astrastore.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts which route answers a given request.
 *
 * <p>Every case here is one that a reordering of {@code application.yaml}
 * would silently break: the routes overlap by design, and the gateway takes
 * the first predicate that matches.
 */
@SpringBootTest
class RouteConfigurationTest {

    private static final String JSON = "application/json";

    @Autowired
    private RouteLocator routeLocator;

    // --- Object paths that collide across three services -----------------

    @Test
    void uploadTakesPrecedenceOverBucketMetadata() {
        assertThat(routeFor(HttpMethod.PUT, "/api/v1/buckets/b1/objects/photos/cat.png"))
                .isEqualTo("upload-route");
    }

    @Test
    void objectListingIsNotSwallowedByTheDownloadRoute() {
        assertThat(routeFor(HttpMethod.GET, "/api/v1/buckets/b1/objects"))
                .isEqualTo("bucket-metadata-route");
    }

    @Test
    void downloadByKeyMatchesASingleKeySegment() {
        assertThat(routeFor(HttpMethod.GET, "/api/v1/buckets/b1/objects/cat.png"))
                .isEqualTo("download-by-key-route");
    }

    @Test
    void recentIsAMetadataListingNotAnObjectId() {
        assertThat(routeFor(HttpMethod.GET, "/api/v1/objects/recent"))
                .isEqualTo("object-recent-route");
    }

    @Test
    void objectBytesGoToDownloadWhenNoJsonIsAskedFor() {
        assertThat(routeFor(HttpMethod.GET, "/api/v1/objects/abc", HttpHeaders.ACCEPT, "*/*"))
                .isEqualTo("download-by-id-route");
        // The three SDKs and the CLI send no Accept header at all.
        assertThat(routeFor(HttpMethod.GET, "/api/v1/objects/abc"))
                .isEqualTo("download-by-id-route");
        assertThat(routeFor(HttpMethod.HEAD, "/api/v1/objects/abc"))
                .isEqualTo("download-by-id-route");
    }

    @Test
    void objectRecordGoesToMetadataWhenJsonIsAskedFor() {
        assertThat(routeFor(HttpMethod.GET, "/api/v1/objects/abc", HttpHeaders.ACCEPT, JSON))
                .isEqualTo("object-detail-route");
    }

    @Test
    void aBrowserNavigationStillDownloadsBytes() {
        String browserAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
        assertThat(routeFor(HttpMethod.GET, "/api/v1/objects/abc", HttpHeaders.ACCEPT, browserAccept))
                .isEqualTo("download-by-id-route");
    }

    @Test
    void objectDeleteIsSoftDeleteInMetadata() {
        assertThat(routeFor(HttpMethod.DELETE, "/api/v1/objects/abc"))
                .isEqualTo("object-delete-route");
    }

    @Test
    void permanentDeleteIsDistinctFromSoftDelete() {
        assertThat(routeFor(HttpMethod.DELETE, "/api/v1/objects/abc/permanent"))
                .isEqualTo("object-permanent-delete-route");
    }

    @Test
    void starAndRestoreReachMetadata() {
        assertThat(routeFor(HttpMethod.PUT, "/api/v1/objects/abc/star")).isEqualTo("object-star-route");
        assertThat(routeFor(HttpMethod.DELETE, "/api/v1/objects/abc/star")).isEqualTo("object-star-route");
        assertThat(routeFor(HttpMethod.POST, "/api/v1/objects/abc/restore"))
                .isEqualTo("object-restore-route");
    }

    // --- Routes the dashboard previously had to bypass -------------------

    @Test
    void starredTrashAndStatsAreRouted() {
        assertThat(routeFor(HttpMethod.GET, "/api/v1/starred")).isEqualTo("starred-route");
        assertThat(routeFor(HttpMethod.GET, "/api/v1/trash")).isEqualTo("trash-route");
        assertThat(routeFor(HttpMethod.POST, "/api/v1/trash/empty")).isEqualTo("trash-route");
        assertThat(routeFor(HttpMethod.GET, "/api/v1/stats")).isEqualTo("stats-route");
    }

    @Test
    void monitoringIsRoutedWithoutARewrite() {
        assertThat(routeFor(HttpMethod.GET, "/api/v1/monitoring/services"))
                .isEqualTo("monitoring-route");
        assertThat(routeFor(HttpMethod.GET, "/api/monitoring/services"))
                .isEqualTo("monitoring-legacy-route");
    }

    @Test
    void serviceHealthIsRouted() {
        assertThat(routeFor(HttpMethod.GET, "/health/metadata")).isEqualTo("service-health-route");
    }

    // --- Auth, including the sub-paths added after this config was written

    @Test
    void authSubPathsFallThroughToTheGenericRoute() {
        assertThat(routeFor(HttpMethod.POST, "/api/auth/login")).isEqualTo("auth-login-route");
        assertThat(routeFor(HttpMethod.POST, "/api/auth/register")).isEqualTo("auth-register-route");
        assertThat(routeFor(HttpMethod.POST, "/api/auth/password/forgot")).isEqualTo("auth-password-route");
        assertThat(routeFor(HttpMethod.POST, "/api/auth/password/reset")).isEqualTo("auth-password-route");
        assertThat(routeFor(HttpMethod.GET, "/api/auth/account/profile")).isEqualTo("auth-route");
        assertThat(routeFor(HttpMethod.GET, "/api/auth/admin/users")).isEqualTo("auth-route");
        assertThat(routeFor(HttpMethod.GET, "/api/auth/audit")).isEqualTo("auth-route");
        assertThat(routeFor(HttpMethod.GET, "/api/auth/keys")).isEqualTo("auth-route");
    }

    // --- Nothing may reach a service's private surface -------------------

    @Test
    void internalAndActuatorPathsAreNotRouted() {
        assertThat(routeFor(HttpMethod.POST, "/internal/v1/auth/api-keys/verify")).isNull();
        assertThat(routeFor(HttpMethod.GET, "/internal/v1/objects/abc")).isNull();
        assertThat(routeFor(HttpMethod.GET, "/actuator/env")).isNull();
        assertThat(routeFor(HttpMethod.GET, "/__astra/unavailable")).isNull();
    }

    /**
     * The legacy aliases rewrite onto {@code /api/v1}, so the prefix cannot be
     * used as a tunnel to a downstream's actuator or internal endpoints.
     */
    @Test
    void legacyAliasesRewriteOntoTheVersionedPrefix() {
        assertThat(rewrittenPath("/api/monitoring/actuator/env"))
                .isEqualTo("/api/v1/monitoring/actuator/env");
        assertThat(rewrittenPath("/api/placement/internal/v1/nodes"))
                .isEqualTo("/api/v1/placement/internal/v1/nodes");
    }

    // --- Helpers ---------------------------------------------------------

    private String routeFor(HttpMethod method, String path) {
        return routeFor(method, path, null, null);
    }

    private String routeFor(HttpMethod method, String path, String header, String value) {
        Route route = match(exchange(method, path, header, value));
        return route == null ? null : route.getId();
    }

    /**
     * Applies the rewrite and reads the URI the gateway would call. Only the
     * first filter runs — the rewrite is declared ahead of the limiter and the
     * breaker on every legacy route, and neither of those has anything to say
     * about the path.
     */
    private String rewrittenPath(String path) {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, path, null, null);
        Route route = match(exchange);
        assertThat(route).as("no route matched %s", path).isNotNull();

        // Run the route's filters in order rather than assuming the rewrite is
        // first — these routes also carry a rate limiter, and which one sorts
        // ahead is not something this test should depend on. Filters that need
        // infrastructure we do not stand up here (Redis, for the limiter) are
        // skipped; the rewrite is the one under test.
        for (GatewayFilter filter : route.getFilters()) {
            try {
                filter.filter(exchange, ex -> Mono.empty()).block();
            } catch (Exception ignored) {
                continue;
            }
            if (exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR) != null) {
                break;
            }
        }

        URI rewritten = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        assertThat(rewritten).as("no rewrite applied to %s", path).isNotNull();
        return rewritten.getPath();
    }

    private Route match(MockServerWebExchange exchange) {
        return routeLocator.getRoutes()
                .concatMap(route -> Mono.from(route.getPredicate().apply(exchange))
                        .filter(Boolean::booleanValue)
                        .map(matched -> route))
                .next()
                .block();
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path,
                                                  String header, String value) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.method(method, URI.create("http://gateway.test" + path));
        if (header != null) {
            builder.header(header, value);
        }
        return MockServerWebExchange.from(builder.build());
    }
}

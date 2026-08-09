package com.astrastore.placement.config;

import com.astrastore.placement.registry.NodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Central Spring configuration for the Placement service.
 *
 * <ul>
 *   <li>Enables {@link EnableConfigurationProperties} so {@link ClusterProperties}
 *       is bound and available as a bean.</li>
 *   <li>Enables scheduling via {@link EnableScheduling} so
 *       {@code @Scheduled} on the HeartbeatService fires.</li>
 *   <li>Provides a {@link RestTemplate} bean pre-configured with the
 *       heartbeat timeout from config.</li>
 *   <li>Populates the {@link NodeRegistry} with all nodes declared in YAML
 *       on application startup.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ClusterProperties.class)
@RequiredArgsConstructor
@Slf4j
public class PlacementConfig {

    private final ClusterProperties clusterProperties;

    /**
     * Provides a {@link RestTemplate} whose connect + read timeouts match
     * the configured heartbeat timeout. Uses {@link SimpleClientHttpRequestFactory}
     * directly for stable, version-agnostic timeout configuration.
     */
    @Bean(name = "heartbeatRestTemplate")
    public RestTemplate heartbeatRestTemplate() {
        int timeoutMs = clusterProperties.getHeartbeat().getTimeoutMs();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        return new RestTemplate(factory);
    }

    /**
     * Seeds the {@link NodeRegistry} with all nodes declared in
     * {@code astrastore.cluster.nodes} at application startup.
     *
     * <p>This bean depends on the registry so Spring initialises the registry
     * first, then this method runs to populate it.</p>
     */
    @Bean
    public Boolean seedNodeRegistry(NodeRegistry nodeRegistry) {
        clusterProperties.getNodes().forEach(nc -> {
            nodeRegistry.registerNode(nc.getId(), nc.getUrl());
            log.info("Registered storage node — id={}, url={}", nc.getId(), nc.getUrl());
        });
        log.info("Node registry seeded with {} nodes", clusterProperties.getNodes().size());
        return Boolean.TRUE; // sentinel; only the side-effect matters
    }
}

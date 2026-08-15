package com.astrastore.placement.controller;

import com.astrastore.placement.config.ClusterProperties;
import com.astrastore.placement.model.StorageNode;
import com.astrastore.placement.registry.NodeRegistry;
import com.astrastore.placement.service.ClusterCapacityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@code GET /api/v1/cluster/status}: the dashboard binds
 * directly to these field names, so both the preserved keys and the new
 * capacity keys are pinned here.
 */
class ClusterStatusControllerTest {

    private static final long GIB = 1024L * 1024L * 1024L;

    private NodeRegistry registry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
        ClusterProperties properties = new ClusterProperties();
        properties.setReplicationFactor(2);

        ClusterCapacityService capacityService = new ClusterCapacityService(registry, properties);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClusterStatusController(registry, capacityService))
                .build();
    }

    private void reportNode(String id, long capacity, long used, long chunks, long hostFree) {
        registry.registerNode(id, "http://" + id + ":8088");
        StorageNode node = registry.findById(id).orElseThrow();
        node.getCapacityBytes().set(capacity);
        node.getUsedBytes().set(used);
        node.getAvailableBytes().set(capacity - used);
        node.getChunkCount().set(chunks);
        node.getHostDiskFreeBytes().set(hostFree);
        node.getCapacityReported().set(true);
    }

    @Test
    void keepsTheFieldNamesTheDashboardAlreadyUses() throws Exception {
        reportNode("storage-node-1", 20 * GIB, GIB, 4, 400 * GIB);

        mockMvc.perform(get("/api/v1/cluster/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNodes").value(1))
                .andExpect(jsonPath("$.healthyNodes").value(1))
                .andExpect(jsonPath("$.degradedNodes").value(0))
                .andExpect(jsonPath("$.downNodes").value(0))
                .andExpect(jsonPath("$.recoveringNodes").value(0))
                .andExpect(jsonPath("$.eligibleNodes").value(1))
                .andExpect(jsonPath("$.checkedAt").isNotEmpty())
                .andExpect(jsonPath("$.nodes[0].nodeId").value("storage-node-1"));
    }

    @Test
    void totalsAreQuotasAndNotTheThriceCountedHostDrive() throws Exception {
        // All three "nodes" share one 400 GiB host drive, as they do in compose.
        reportNode("storage-node-1", 20 * GIB, GIB, 4, 400 * GIB);
        reportNode("storage-node-2", 20 * GIB, GIB, 4, 400 * GIB);
        reportNode("storage-node-3", 20 * GIB, 0L, 0, 400 * GIB);

        mockMvc.perform(get("/api/v1/cluster/status"))
                .andExpect(status().isOk())
                // 60 GiB of quota, not 1.2 TiB of imaginary disk.
                .andExpect(jsonPath("$.totalCapacityBytes").value(60 * GIB))
                .andExpect(jsonPath("$.usedBytes").value(2 * GIB))
                .andExpect(jsonPath("$.availableBytes").value(58 * GIB))
                .andExpect(jsonPath("$.totalChunkCount").value(8))
                .andExpect(jsonPath("$.reportingNodes").value(3))
                .andExpect(jsonPath("$.insufficientData").value(false))
                // Host disk stays per-node and advisory; there is no cluster
                // total for it, precisely so it cannot be summed again.
                .andExpect(jsonPath("$.nodes[0].hostDiskFreeBytes").value(400 * GIB))
                .andExpect(jsonPath("$.hostDiskFreeBytes").doesNotExist());
    }

    @Test
    void publishesRawAndLogicalBytesSeparately() throws Exception {
        reportNode("storage-node-1", 20 * GIB, GIB, 4, 400 * GIB);
        reportNode("storage-node-2", 20 * GIB, GIB, 4, 400 * GIB);

        mockMvc.perform(get("/api/v1/cluster/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replicationFactor").value(2))
                .andExpect(jsonPath("$.rawBytesStored").value(2 * GIB))
                .andExpect(jsonPath("$.logicalBytesStored").value(GIB))
                .andExpect(jsonPath("$.replicationOverheadBytes").value(GIB))
                .andExpect(jsonPath("$.logicalBytesIsEstimate").value(true));
    }

    @Test
    void ratiosAreNumbersNotPreformattedStrings() throws Exception {
        reportNode("storage-node-1", 100L, 25L, 1, 400 * GIB);

        mockMvc.perform(get("/api/v1/cluster/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedRatio").value(instanceOf(Number.class)))
                .andExpect(jsonPath("$.usedRatio").value(0.25))
                .andExpect(jsonPath("$.clusterFreeRatio").value(instanceOf(Number.class)))
                .andExpect(jsonPath("$.nodes[0].usedRatio").value(instanceOf(Number.class)))
                .andExpect(jsonPath("$.nodes[0].diskFreeRatio").value(instanceOf(Number.class)));
    }

    @Test
    void freshClusterReportsUnknownCapacityRatherThanZero() throws Exception {
        registry.registerNode("storage-node-1", "http://storage-node-1:8088");

        mockMvc.perform(get("/api/v1/cluster/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNodes").value(1))
                .andExpect(jsonPath("$.insufficientData").value(true))
                .andExpect(jsonPath("$.totalCapacityBytes").value(nullValue()))
                .andExpect(jsonPath("$.usedBytes").value(nullValue()))
                .andExpect(jsonPath("$.usedRatio").value(nullValue()))
                .andExpect(jsonPath("$.nodes[0].capacityReported").value(false))
                .andExpect(jsonPath("$.nodes[0].capacityBytes").value(nullValue()));
    }
}

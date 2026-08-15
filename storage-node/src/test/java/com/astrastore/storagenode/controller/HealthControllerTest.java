package com.astrastore.storagenode.controller;

import com.astrastore.storagenode.config.StorageConfig;
import com.astrastore.storagenode.config.StorageNodeProperties;
import com.astrastore.storagenode.service.NodeUsageTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the shape of the heartbeat the placement service parses — in
 * particular that capacity is the node's quota, not the host drive, and that
 * every ratio is a number the UI can chart without parsing text.
 */
class HealthControllerTest {

    private MockMvc mockMvc;
    private StorageNodeProperties properties;
    private NodeUsageTracker tracker;

    @BeforeEach
    void setUp(@TempDir Path root) {
        properties = new StorageNodeProperties();
        properties.setId("storage-node-1");
        properties.setCapacityBytes(1_000L);
        properties.setStorageRoot(root.toString());

        tracker = new NodeUsageTracker(properties);
        StorageConfig storageConfig = new StorageConfig(properties);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController(storageConfig, properties, tracker))
                .build();
    }

    @Test
    void reportsConfiguredQuotaAndRealUsage() throws Exception {
        tracker.onChunkStored(250L);

        mockMvc.perform(get("/api/v1/health/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.nodeId").value("storage-node-1"))
                .andExpect(jsonPath("$.capacityBytes").value(1_000))
                .andExpect(jsonPath("$.usedBytes").value(250))
                .andExpect(jsonPath("$.availableBytes").value(750))
                .andExpect(jsonPath("$.chunkCount").value(1))
                // Numeric, not "25.00%": the dashboard must never parse text.
                .andExpect(jsonPath("$.usedRatio").value(0.25))
                .andExpect(jsonPath("$.usedRatio").value(instanceOf(Number.class)));
    }

    @Test
    void hostDiskIsReportedSeparatelyAndIsNotTheCapacity() throws Exception {
        mockMvc.perform(get("/api/v1/health/heartbeat"))
                .andExpect(status().isOk())
                // The host drive is advisory only. It is far larger than the
                // 1 000-byte quota, which is exactly why summing it across
                // three containers used to invent a 10 TB cluster.
                // Long, not int: a real host drive overflows an Integer, and
                // Hamcrest will not compare across the two boxed types.
                .andExpect(jsonPath("$.hostDiskFreeBytes").value(greaterThan(1_000L)))
                .andExpect(jsonPath("$.capacityBytes").value(1_000));
    }

    @Test
    void availableBytesIsFlooredAtZeroWhenOverQuota() throws Exception {
        tracker.onChunkStored(1_500L);

        mockMvc.perform(get("/api/v1/health/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedBytes").value(1_500))
                .andExpect(jsonPath("$.availableBytes").value(0))
                .andExpect(jsonPath("$.usedRatio").value(1.5));
    }

    @Test
    void flagsUsageAsUnmeasuredBeforeTheFirstWalk() throws Exception {
        mockMvc.perform(get("/api/v1/health/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usageMeasured").value(false))
                .andExpect(jsonPath("$.usageReconciledAt").value(nullValue()));

        tracker.reconcile();

        mockMvc.perform(get("/api/v1/health/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usageMeasured").value(true))
                .andExpect(jsonPath("$.usageReconciledAt").isNotEmpty());
    }
}

package com.astrastore.placement.controller;

import com.astrastore.shared.strategy.PlacementStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlacementControllerTest {

    private MockMvc mockMvc;
    private PlacementStrategy placementStrategyService;

    @BeforeEach
    void setUp() {
        placementStrategyService = Mockito.mock(PlacementStrategy.class);
        PlacementController controller = new PlacementController(placementStrategyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getNextTargetNode_success() throws Exception {
        when(placementStrategyService.getNextTargetNode()).thenReturn("http://node-1:8088");

        mockMvc.perform(get("/api/v1/placement/next"))
                .andExpect(status().isOk())
                .andExpect(content().string("http://node-1:8088"));
    }

    @Test
    void getNextTargetNode_noNodesAvailable() throws Exception {
        when(placementStrategyService.getNextTargetNode()).thenReturn(null);

        mockMvc.perform(get("/api/v1/placement/next"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getNextTargetNodes_success() throws Exception {
        when(placementStrategyService.getNextTargetNodes(2, "http://node-1:8088"))
                .thenReturn(List.of("http://node-2:8088", "http://node-3:8088"));

        mockMvc.perform(get("/api/v1/placement/next/multiple")
                        .param("count", "2")
                        .param("excludeNode", "http://node-1:8088"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"http://node-2:8088\",\"http://node-3:8088\"]"));
    }

    @Test
    void getNextTargetNodes_noNodesAvailable() throws Exception {
        when(placementStrategyService.getNextTargetNodes(2, "http://node-1:8088"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/placement/next/multiple")
                        .param("count", "2")
                        .param("excludeNode", "http://node-1:8088"))
                .andExpect(status().isServiceUnavailable());
    }
}

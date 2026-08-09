package com.astrastore.replication.placement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

@RestClientTest(
        components = RemotePlacementStrategy.class,
        properties = "astrastore.placement.url=http://placement-service:8085"
)
class RemotePlacementStrategyTest {

    @Autowired
    private RemotePlacementStrategy remotePlacementStrategy;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    void getNextTargetNode_success() {
        mockServer.expect(requestTo("http://placement-service:8085/api/v1/placement/next"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("http://node-1:8088", MediaType.TEXT_PLAIN));

        String target = remotePlacementStrategy.getNextTargetNode();
        
        assertEquals("http://node-1:8088", target);
        mockServer.verify();
    }

    @Test
    void getNextTargetNode_failure() {
        mockServer.expect(requestTo("http://placement-service:8085/api/v1/placement/next"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        String target = remotePlacementStrategy.getNextTargetNode();
        
        assertNull(target);
        mockServer.verify();
    }

    @Test
    void getNextTargetNodes_success() {
        mockServer.expect(requestTo("http://placement-service:8085/api/v1/placement/next/multiple?count=2&excludeNode=http://node-1:8088"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[\"http://node-2:8088\", \"http://node-3:8088\"]", MediaType.APPLICATION_JSON));

        List<String> targets = remotePlacementStrategy.getNextTargetNodes(2, "http://node-1:8088");
        
        assertEquals(2, targets.size());
        assertEquals("http://node-2:8088", targets.get(0));
        assertEquals("http://node-3:8088", targets.get(1));
        mockServer.verify();
    }
}

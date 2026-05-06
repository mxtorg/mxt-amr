package com.flowengine.service;

import com.flowengine.core.executor.FlowEngine;
import com.flowengine.dto.*;
import com.flowengine.domain.enums.NodeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FlowServiceTest {

    @Autowired
    private FlowService flowService;

    @Autowired
    private FlowEngine flowEngine;

    @Test
    void testCreateAndGetFlow() {
        FlowDefinitionDTO dto = new FlowDefinitionDTO();
        dto.setId("test-flow-001");
        dto.setName("测试流程");

        List<Object> nodes = new ArrayList<>();
        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "start_1");
        startNode.put("nodeType", NodeType.START.name());
        startNode.put("name", "开始");
        startNode.put("nids", Arrays.asList("http_1"));
        startNode.put("pids", new ArrayList<>());
        startNode.put("position", Map.of("x", 100, "y", 200));
        nodes.add(startNode);

        Map<String, Object> httpNode = new HashMap<>();
        httpNode.put("id", "http_1");
        httpNode.put("nodeType", NodeType.HTTP.name());
        httpNode.put("name", "HTTP请求");
        httpNode.put("pids", Arrays.asList("start_1"));
        httpNode.put("nids", Arrays.asList("end_1"));
        httpNode.put("source", "测试数据源");
        httpNode.put("groupId", 0);
        httpNode.put("api", "http://localhost:8080/api/test");
        httpNode.put("method", "GET");
        httpNode.put("inputSchema", Map.of());
        httpNode.put("outputSchema", Map.of());
        httpNode.put("position", Map.of("x", 300, "y", 200));
        nodes.add(httpNode);

        Map<String, Object> endNode = new HashMap<>();
        endNode.put("id", "end_1");
        endNode.put("nodeType", NodeType.END.name());
        endNode.put("name", "结束");
        endNode.put("pids", Arrays.asList("http_1"));
        endNode.put("nids", new ArrayList<>());
        endNode.put("position", Map.of("x", 500, "y", 200));
        nodes.add(endNode);

        dto.setNodes(nodes);

        List<EdgeDTO> edges = new ArrayList<>();
        EdgeDTO edge1 = new EdgeDTO();
        edge1.setSource("start_1");
        edge1.setTarget("http_1");
        edges.add(edge1);

        EdgeDTO edge2 = new EdgeDTO();
        edge2.setSource("http_1");
        edge2.setTarget("end_1");
        edges.add(edge2);

        dto.setEdges(edges);

        String flowId = flowService.createFlow(dto);
        assertNotNull(flowId);

        FlowDefinitionDTO retrieved = flowService.getFlow(flowId);
        assertNotNull(retrieved);
        assertEquals("测试流程", retrieved.getName());
        assertEquals(3, retrieved.getNodes().size());
    }

    @Test
    void testExecuteFlow() {
        FlowDefinitionDTO dto = new FlowDefinitionDTO();
        dto.setId("exec-test-flow");
        dto.setName("执行测试流程");
        dto.setVersion(1);

        List<Object> nodes = new ArrayList<>();
        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "start_1");
        startNode.put("nodeType", NodeType.START.name());
        startNode.put("name", "开始");
        startNode.put("nids", Arrays.asList("end_1"));
        startNode.put("pids", new ArrayList<>());
        startNode.put("position", Map.of("x", 100, "y", 200));
        nodes.add(startNode);

        Map<String, Object> endNode = new HashMap<>();
        endNode.put("id", "end_1");
        endNode.put("nodeType", NodeType.END.name());
        endNode.put("name", "结束");
        endNode.put("pids", Arrays.asList("start_1"));
        endNode.put("nids", new ArrayList<>());
        endNode.put("position", Map.of("x", 300, "y", 200));
        nodes.add(endNode);

        dto.setNodes(nodes);

        List<EdgeDTO> edges = new ArrayList<>();
        EdgeDTO edge = new EdgeDTO();
        edge.setSource("start_1");
        edge.setTarget("end_1");
        edges.add(edge);
        dto.setEdges(edges);

        flowService.createFlow(dto);

        FlowResultDTO result = flowEngine.execute(dto.getId(), dto, Map.of("testKey", "testValue"));
        assertNotNull(result);
        assertNotNull(result.getExecutionId());
    }
}

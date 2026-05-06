package com.flowengine.core.executor;

import com.flowengine.core.context.FlowContext;
import com.flowengine.domain.entity.FlowExec;
import com.flowengine.domain.entity.NodeExec;
import com.flowengine.domain.enums.NodeType;
import com.flowengine.dto.FlowDefinitionDTO;
import com.flowengine.dto.FlowResultDTO;
import com.flowengine.dto.NodeResultDTO;
import com.flowengine.dto.EdgeDTO;
import com.flowengine.repository.FlowExecRepository;
import com.flowengine.repository.NodeExecRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FlowEngine {

    private final Map<String, NodeExecutor> executors;
    private final FlowExecRepository flowExecRepository;
    private final NodeExecRepository nodeExecRepository;
    private final ObjectMapper objectMapper;

    public FlowEngine(List<NodeExecutor> executorList,
                      FlowExecRepository flowExecRepository,
                      NodeExecRepository nodeExecRepository) {
        this.executors = new ConcurrentHashMap<>();
        for (NodeExecutor executor : executorList) {
            executors.put(executor.getType().name(), executor);
        }
        this.flowExecRepository = flowExecRepository;
        this.nodeExecRepository = nodeExecRepository;
        this.objectMapper = new ObjectMapper();
    }

    public FlowResultDTO execute(String flowId, FlowDefinitionDTO flowDef, Map<String, Object> input) {
        String executionId = UUID.randomUUID().toString();
        log.info("Starting flow execution: flowId={}, executionId={}", flowId, executionId);

        FlowExec flowExec = new FlowExec();
        flowExec.setId(executionId);
        flowExec.setFlowId(flowId);
        flowExec.setFlowVersion(flowDef.getVersion() != null ? flowDef.getVersion() : 1);
        flowExec.setInputParam(objectMapper.writeValueAsString(input));
        flowExec.setTraceId(UUID.randomUUID().toString());
        flowExecRepository.save(flowExec);

        FlowContext context = new FlowContext();
        context.setFlowId(flowId);
        context.setExecutionId(executionId);
        context.setGlobalVars(input != null ? input : new HashMap<>());

        Map<String, NodeResultDTO> nodeResults = new ConcurrentHashMap<>();
        long startTime = System.currentTimeMillis();
        String finalStatus = "SUCCESS";

        try {
            List<Object> nodes = (List<Object>) (List<?>) flowDef.getNodes();
            List<EdgeDTO> edges = flowDef.getEdges();

            Map<String, List<String>> adj = buildAdjacencyList(nodes, edges);
            Map<String, Integer> inDegree = computeInDegree(nodes, edges);
            Queue<String> readyQueue = new LinkedList<>();

            for (String nodeId : inDegree.keySet()) {
                if (inDegree.get(nodeId) == 0) {
                    readyQueue.offer(nodeId);
                }
            }

            Map<String, Object> nodeMap = new HashMap<>();
            for (Object node : nodes) {
                Map<String, Object> nodeMapEntry = objectMapper.convertValue(node, Map.class);
                nodeMap.put((String) nodeMapEntry.get("id"), node);
            }

            while (!readyQueue.isEmpty()) {
                String nodeId = readyQueue.poll();
                Object node = nodeMap.get(nodeId);

                if (node == null) continue;

                Map<String, Object> nodeData = objectMapper.convertValue(node, Map.class);
                String nodeType = nodeData.get("nodeType").toString();

                log.info("Executing node: id={}, type={}", nodeId, nodeType);

                NodeExec nodeExec = new NodeExec();
                nodeExec.setExecId(executionId);
                nodeExec.setNodeId(nodeId);
                nodeExec.setNodeType(nodeType);
                nodeExecRepository.save(nodeExec);

                NodeResultDTO result;
                if (nodeType.equals(NodeType.START.name()) || nodeType.equals(NodeType.END.name())) {
                    result = new NodeResultDTO(true, nodeData.get("name"), null, 0L);
                } else {
                    NodeExecutor executor = executors.get(nodeType);
                    if (executor != null) {
                        result = executor.execute(node, context);
                    } else {
                        result = new NodeResultDTO(false, null, "No executor for type: " + nodeType, 0L);
                    }
                }

                nodeResults.put(nodeId, result);
                context.setNodeOutput(nodeId, result.getOutput());

                if (!result.getSuccess()) {
                    finalStatus = "FAILED";
                    log.error("Node execution failed: id={}, error={}", nodeId, result.getErrorMsg());
                }

                List<String> children = adj.getOrDefault(nodeId, Collections.emptyList());
                for (String child : children) {
                    int newDegree = inDegree.get(child) - 1;
                    inDegree.put(child, newDegree);
                    if (newDegree == 0) {
                        readyQueue.offer(child);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Flow execution error", e);
            finalStatus = "FAILED";
        }

        long totalDuration = System.currentTimeMillis() - startTime;

        flowExec.setStatus(finalStatus);
        flowExec.setEndTime(LocalDateTime.now());
        flowExec.setDurationMs((int) totalDuration);
        flowExec.setOutputResult(objectMapper.writeValueAsString(nodeResults));
        flowExecRepository.save(flowExec);

        log.info("Flow execution completed: executionId={}, status={}, duration={}ms",
            executionId, finalStatus, totalDuration);

        FlowResultDTO flowResult = new FlowResultDTO();
        flowResult.setExecutionId(executionId);
        flowResult.setStatus(finalStatus);
        flowResult.setNodeResults(nodeResults);
        flowResult.setTotalDurationMs(totalDuration);

        return flowResult;
    }

    private Map<String, List<String>> buildAdjacencyList(List<Object> nodes, List<EdgeDTO> edges) {
        Map<String, List<String>> adj = new HashMap<>();
        for (Object node : nodes) {
            Map<String, Object> nodeMap = objectMapper.convertValue(node, Map.class);
            String id = (String) nodeMap.get("id");
            adj.put(id, new ArrayList<>());
        }
        for (EdgeDTO edge : edges) {
            adj.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
        }
        return adj;
    }

    private Map<String, Integer> computeInDegree(List<Object> nodes, List<EdgeDTO> edges) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (Object node : nodes) {
            Map<String, Object> nodeMap = objectMapper.convertValue(node, Map.class);
            inDegree.put((String) nodeMap.get("id"), 0);
        }
        for (EdgeDTO edge : edges) {
            inDegree.merge(edge.getTarget(), 1, Integer::sum);
        }
        return inDegree;
    }
}

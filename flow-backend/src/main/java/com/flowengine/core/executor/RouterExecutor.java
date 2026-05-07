package com.flowengine.core.executor;

import com.flowengine.core.context.FlowContext;
import com.flowengine.domain.enums.NodeType;
import com.flowengine.dto.NodeResultDTO;
import com.flowengine.dto.RouterNodeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class RouterExecutor implements NodeExecutor {

    private final ExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final Map<String, NodeExecutor> executorMap;

    public RouterExecutor(Map<String, NodeExecutor> executorMap) {
        this.executorMap = executorMap;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newCachedThreadPool();
    }

    @Override
    public NodeType getType() {
        return NodeType.ROUTER;
    }

    @Override
    public NodeResultDTO execute(Object node, FlowContext context) {
        long start = System.currentTimeMillis();
        try {
            RouterNodeDTO routerNode = objectMapper.convertValue(node, RouterNodeDTO.class);
            log.info("Executing ROUTER node: {}", routerNode.getId());

            Map<String, Object> strategy = routerNode.getStrategySchema();
            String strategyType = strategy != null && strategy.get("type") != null
                ? strategy.get("type").toString() : "ALL";
            String waitMode = strategy != null && strategy.get("waitMode") != null
                ? strategy.get("waitMode").toString() : "COMPLETABLE_FUTURE";

            List<Object> children = getChildNodes(routerNode, context);
            Map<Integer, List<Object>> groups = groupByGroupId(children);

            Map<Integer, List<NodeResultDTO>> groupResults = new HashMap<>();

            for (Map.Entry<Integer, List<Object>> entry : groups.entrySet()) {
                List<Object> groupNodes = entry.getValue();
                List<CompletableFuture<NodeResultDTO>> futures = new ArrayList<>();

                for (Object child : groupNodes) {
                    CompletableFuture<NodeResultDTO> future = CompletableFuture.supplyAsync(
                        () -> executeChild(child, context),
                        executorService
                    );
                    futures.add(future);
                }

                List<NodeResultDTO> results;
                if ("COMPLETABLE_FUTURE".equals(waitMode)) {
                    results = futures.stream()
                        .map(CompletableFuture::join)
                        .collect(java.util.stream.Collectors.toList());
                } else {
                    try {
                        results = futures.stream()
                            .map(f -> {
                                try {
                                    return f.get(30, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                } catch (ExecutionException e) {
                                    throw new RuntimeException(e);
                                } catch (TimeoutException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .collect(java.util.stream.Collectors.toList());
                    } catch (Exception e) {
                        log.warn("Router group {} timeout", entry.getKey());
                        results = futures.stream()
                            .map(f -> {
                                if (f.isDone()) return f.join();
                                return new NodeResultDTO(false, null, "TIMEOUT", 0L);
                            })
                            .collect(java.util.stream.Collectors.toList());
                    }
                }
                groupResults.put(entry.getKey(), results);
            }

            Object mappedData = executeMapping(routerNode.getMappingSchema(), groupResults, context);
            long duration = System.currentTimeMillis() - start;

            context.setNodeOutput(routerNode.getId(), mappedData);
            return new NodeResultDTO(true, mappedData, null, duration);

        } catch (Exception e) {
            log.error("Router execution failed", e);
            long duration = System.currentTimeMillis() - start;
            return new NodeResultDTO(false, null, e.getMessage(), duration);
        }
    }

    private List<Object> getChildNodes(RouterNodeDTO routerNode, FlowContext context) {
        List<Object> children = new ArrayList<>();
        Object childrenObj = context.getNodeOutput("_CHILDREN_" + routerNode.getId());
        if (childrenObj instanceof List) {
            children.addAll((List) childrenObj);
        }
        return children;
    }

    private Map<Integer, List<Object>> groupByGroupId(List<Object> children) {
        Map<Integer, List<Object>> groups = new HashMap<>();
        for (Object child : children) {
            try {
                Map<String, Object> childMap = objectMapper.convertValue(child, Map.class);
                Integer groupId = childMap.get("groupId") != null
                    ? ((Number) childMap.get("groupId")).intValue() : 0;
                groups.computeIfAbsent(groupId, k -> new ArrayList<>()).add(child);
            } catch (Exception e) {
                log.warn("Failed to parse child node for grouping", e);
            }
        }
        return groups;
    }

    private NodeResultDTO executeChild(Object child, FlowContext context) {
        try {
            Map<String, Object> childMap = objectMapper.convertValue(child, Map.class);
            String nodeType = childMap.get("nodeType").toString();
            NodeExecutor executor = executorMap.get(nodeType);
            if (executor != null) {
                return executor.execute(child, context);
            }
            return new NodeResultDTO(false, null, "No executor for type: " + nodeType, 0L);
        } catch (Exception e) {
            return new NodeResultDTO(false, null, e.getMessage(), 0L);
        }
    }

    private Object executeMapping(Map<String, Object> mappingSchema,
                                   Map<Integer, List<NodeResultDTO>> groupResults,
                                   FlowContext context) {
        Map<String, Object> result = new HashMap<>();
        if (mappingSchema == null) return result;

        List<Map<String, Object>> mappings = (List<Map<String, Object>>) mappingSchema.get("mappings");
        if (mappings == null) return result;

        for (Map<String, Object> mapping : mappings) {
            String source = (String) mapping.get("source");
            String target = (String) mapping.get("target");
            try {
                Object value = resolveSource(source, groupResults, context);
                setNestedValue(result, target, value);
            } catch (Exception e) {
                log.warn("Mapping failed: source={}, target={}", source, target, e);
            }
        }
        return result;
    }

    private Object resolveSource(String source, Map<Integer, List<NodeResultDTO>> groupResults, FlowContext context) {
        if (source == null) return null;
        if (source.startsWith("${") && source.endsWith("}")) {
            String expr = source.substring(2, source.length() - 1);
            if (expr.startsWith("group")) {
                return resolveGroupRef(expr, groupResults);
            }
            return context.resolveVariable(expr);
        }
        return source;
    }

    private Object resolveGroupRef(String expr, Map<Integer, List<NodeResultDTO>> groupResults) {
        return groupResults;
    }

    private void setNestedValue(Map<String, Object> target, String path, Object value) {
        if (path == null) return;
        String[] keys = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(keys[i], k -> new HashMap<>());
        }
        current.put(keys[keys.length - 1], value);
    }
}

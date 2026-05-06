package com.flowengine.core.context;

import lombok.Data;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class FlowContext {
    private String flowId;
    private String executionId;
    private Map<String, Object> globalVars;
    private Map<String, Object> nodeOutputs;
    private Map<String, Object> config;

    public FlowContext() {
        this.globalVars = new ConcurrentHashMap<>();
        this.nodeOutputs = new ConcurrentHashMap<>();
        this.config = new ConcurrentHashMap<>();
    }

    public void setNodeOutput(String nodeId, Object output) {
        nodeOutputs.put(nodeId, output);
    }

    public Object getNodeOutput(String nodeId) {
        return nodeOutputs.get(nodeId);
    }

    public Object resolveVariable(String expression) {
        if (expression == null || !expression.startsWith("${") || !expression.endsWith("}")) {
            return expression;
        }
        String expr = expression.substring(2, expression.length() - 1);
        return resolveExpr(expr);
    }

    private Object resolveExpr(String expr) {
        if (expr.startsWith("input.")) {
            return getFromMap(globalVars, expr.substring(6));
        }
        if (expr.contains(".output.")) {
            String[] parts = expr.split("\\.output\\.");
            String nodeId = parts[0];
            String path = parts.length > 1 ? parts[1] : null;
            Object nodeOutput = nodeOutputs.get(nodeId);
            if (nodeOutput == null) return null;
            return path != null ? getFromMap((Map<String, Object>) nodeOutput, path) : nodeOutput;
        }
        return getFromMap(globalVars, expr);
    }

    private Object getFromMap(Map<String, Object> map, String path) {
        if (map == null || path == null) return null;
        String[] keys = path.split("\\.");
        Object current = map;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }
}

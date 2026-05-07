package com.flowengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowengine.domain.entity.*;
import com.flowengine.dto.*;
import com.flowengine.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FlowService {

    private final FlowDefRepository flowDefRepository;
    private final NodeDefRepository nodeDefRepository;
    private final NodeParamRepository nodeParamRepository;
    private final EdgeDefRepository edgeDefRepository;
    private final ObjectMapper objectMapper;

    public FlowService(FlowDefRepository flowDefRepository,
                       NodeDefRepository nodeDefRepository,
                       NodeParamRepository nodeParamRepository,
                       EdgeDefRepository edgeDefRepository) {
        this.flowDefRepository = flowDefRepository;
        this.nodeDefRepository = nodeDefRepository;
        this.nodeParamRepository = nodeParamRepository;
        this.edgeDefRepository = edgeDefRepository;
        this.objectMapper = new ObjectMapper();
    }

    public List<FlowDefinitionDTO> listFlows() {
        return flowDefRepository.findAll().stream()
            .map(this::toFlowDTO)
            .collect(Collectors.toList());
    }

    public FlowDefinitionDTO getFlow(String flowId) {
        FlowDef flowDef = flowDefRepository.findById(flowId)
            .orElseThrow(() -> new RuntimeException("Flow not found: " + flowId));
        return toFlowDTO(flowDef);
    }

    @Transactional
    public String createFlow(FlowDefinitionDTO dto) throws JsonProcessingException {
        FlowDef flowDef = new FlowDef();
        flowDef.setId(dto.getId() != null ? dto.getId() : UUID.randomUUID().toString());
        flowDef.setName(dto.getName());
        flowDef.setDescription(dto.getDescription());
        flowDef.setDagJson(objectMapper.writeValueAsString(dto));
        flowDefRepository.save(flowDef);

        if (dto.getNodes() != null) {
            for (Object nodeObj : dto.getNodes()) {
                Map<String, Object> nodeMap = objectMapper.convertValue(nodeObj, Map.class);
                saveNode(flowDef.getId(), nodeMap);
            }
        }

        if (dto.getEdges() != null) {
            for (EdgeDTO edge : dto.getEdges()) {
                EdgeDef edgeDef = new EdgeDef();
                edgeDef.setFlowId(flowDef.getId());
                edgeDef.setFromNode(edge.getSource());
                edgeDef.setToNode(edge.getTarget());
                edgeDef.setCondition(edge.getCondition());
                edgeDefRepository.save(edgeDef);
            }
        }

        return flowDef.getId();
    }

    @Transactional
    public void updateFlow(String flowId, FlowDefinitionDTO dto) throws JsonProcessingException {
        FlowDef flowDef = flowDefRepository.findById(flowId)
            .orElseThrow(() -> new RuntimeException("Flow not found: " + flowId));

        flowDef.setName(dto.getName());
        flowDef.setDescription(dto.getDescription());
        flowDef.setVersion(flowDef.getVersion() + 1);
        flowDef.setDagJson(objectMapper.writeValueAsString(dto));
        flowDefRepository.save(flowDef);

        nodeDefRepository.deleteByFlowId(flowId);
        nodeParamRepository.deleteByNodeId(flowId);
        edgeDefRepository.deleteByFlowId(flowId);

        if (dto.getNodes() != null) {
            for (Object nodeObj : dto.getNodes()) {
                Map<String, Object> nodeMap = objectMapper.convertValue(nodeObj, Map.class);
                saveNode(flowId, nodeMap);
            }
        }

        if (dto.getEdges() != null) {
            for (EdgeDTO edge : dto.getEdges()) {
                EdgeDef edgeDef = new EdgeDef();
                edgeDef.setFlowId(flowId);
                edgeDef.setFromNode(edge.getSource());
                edgeDef.setToNode(edge.getTarget());
                edgeDef.setCondition(edge.getCondition());
                edgeDefRepository.save(edgeDef);
            }
        }
    }

    @Transactional
    public void deleteFlow(String flowId) {
        nodeDefRepository.deleteByFlowId(flowId);
        edgeDefRepository.deleteByFlowId(flowId);
        flowDefRepository.deleteById(flowId);
    }

    private void saveNode(String flowId, Map<String, Object> nodeMap) throws JsonProcessingException {
        NodeDef nodeDef = new NodeDef();
        nodeDef.setId((String) nodeMap.get("id"));
        nodeDef.setFlowId(flowId);
        nodeDef.setNodeName((String) nodeMap.get("name"));
        nodeDef.setDescription((String) nodeMap.get("description"));

        String nodeTypeStr = (String) nodeMap.get("nodeType");
        if (nodeTypeStr != null) {
            nodeDef.setNodeType(com.flowengine.domain.enums.NodeType.valueOf(nodeTypeStr));
        }

        Object pids = nodeMap.get("pids");
        if (pids instanceof List) {
            nodeDef.setPids(String.join(",", (List<String>) pids));
        }

        Object nids = nodeMap.get("nids");
        if (nids instanceof List) {
            nodeDef.setNids(String.join(",", (List<String>) nids));
        }

        Object position = nodeMap.get("position");
        if (position instanceof Map) {
            Map<String, Object> posMap = (Map<String, Object>) position;
            if (posMap.get("x") != null) {
                nodeDef.setPosX(new java.math.BigDecimal(posMap.get("x").toString()));
            }
            if (posMap.get("y") != null) {
                nodeDef.setPosY(new java.math.BigDecimal(posMap.get("y").toString()));
            }
        }

        nodeDefRepository.save(nodeDef);

        Set<String> excludeFields = Set.of("id", "flowId", "nodeType", "nodeName", "description", "pids", "nids", "position");
        for (Map.Entry<String, Object> entry : nodeMap.entrySet()) {
            if (!excludeFields.contains(entry.getKey()) && entry.getValue() != null) {
                NodeParam param = new NodeParam();
                param.setNodeId(nodeDef.getId());
                param.setParamKey(entry.getKey());
                param.setParamValue(entry.getValue() instanceof String
                    ? (String) entry.getValue()
                    : objectMapper.writeValueAsString(entry.getValue()));
                param.setValueType(entry.getValue() instanceof String ? "STRING" : "JSON");
                nodeParamRepository.save(param);
            }
        }
    }

    private FlowDefinitionDTO toFlowDTO(FlowDef flowDef) {
        FlowDefinitionDTO dto = new FlowDefinitionDTO();
        dto.setId(flowDef.getId());
        dto.setName(flowDef.getName());
        dto.setDescription(flowDef.getDescription());
        dto.setStatus(flowDef.getStatus());
        dto.setVersion(flowDef.getVersion());

        List<NodeDef> nodes = nodeDefRepository.findByFlowId(flowDef.getId());
        List<Map<String, Object>> nodeDTOs = new ArrayList<>();
        for (NodeDef node : nodes) {
            Map<String, Object> nodeMap = new HashMap<>();
            nodeMap.put("id", node.getId());
            nodeMap.put("nodeType", node.getNodeType().name());
            nodeMap.put("name", node.getNodeName());
            nodeMap.put("description", node.getDescription());
            nodeMap.put("pids", node.getPids() != null ? Arrays.asList(node.getPids().split(",")) : new ArrayList<>());
            nodeMap.put("nids", node.getNids() != null ? Arrays.asList(node.getNids().split(",")) : new ArrayList<>());
            nodeMap.put("position", Map.of("x", node.getPosX(), "y", node.getPosY()));

            List<NodeParam> params = nodeParamRepository.findByNodeId(node.getId());
            for (NodeParam param : params) {
                try {
                    if ("JSON".equals(param.getValueType())) {
                        nodeMap.put(param.getParamKey(), objectMapper.readValue(param.getParamValue(), Object.class));
                    } else {
                        nodeMap.put(param.getParamKey(), param.getParamValue());
                    }
                } catch (Exception e) {
                    nodeMap.put(param.getParamKey(), param.getParamValue());
                }
            }
            nodeDTOs.add(nodeMap);
        }
        dto.setNodes((List) nodeDTOs);

        List<EdgeDef> edges = edgeDefRepository.findByFlowId(flowDef.getId());
        List<EdgeDTO> edgeDTOs = edges.stream().map(e -> {
            EdgeDTO edge = new EdgeDTO();
            edge.setId(e.getId().toString());
            edge.setSource(e.getFromNode());
            edge.setTarget(e.getToNode());
            edge.setCondition(e.getCondition());
            return edge;
        }).collect(Collectors.toList());
        dto.setEdges(edgeDTOs);

        return dto;
    }
}

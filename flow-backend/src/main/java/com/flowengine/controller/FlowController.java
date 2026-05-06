package com.flowengine.controller;

import com.flowengine.core.executor.FlowEngine;
import com.flowengine.domain.entity.FlowExec;
import com.flowengine.dto.*;
import com.flowengine.repository.FlowExecRepository;
import com.flowengine.repository.NodeExecRepository;
import com.flowengine.service.FlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class FlowController {

    private final FlowService flowService;
    private final FlowEngine flowEngine;
    private final FlowExecRepository flowExecRepository;
    private final NodeExecRepository nodeExecRepository;

    public FlowController(FlowService flowService,
                          FlowEngine flowEngine,
                          FlowExecRepository flowExecRepository,
                          NodeExecRepository nodeExecRepository) {
        this.flowService = flowService;
        this.flowEngine = flowEngine;
        this.flowExecRepository = flowExecRepository;
        this.nodeExecRepository = nodeExecRepository;
    }

    @GetMapping("/flows")
    public List<FlowDefinitionDTO> listFlows() {
        return flowService.listFlows();
    }

    @GetMapping("/flows/{flowId}")
    public FlowDefinitionDTO getFlow(@PathVariable String flowId) {
        return flowService.getFlow(flowId);
    }

    @PostMapping("/flows")
    public String createFlow(@RequestBody FlowDefinitionDTO dto) {
        return flowService.createFlow(dto);
    }

    @PutMapping("/flows/{flowId}")
    public void updateFlow(@PathVariable String flowId, @RequestBody FlowDefinitionDTO dto) {
        flowService.updateFlow(flowId, dto);
    }

    @DeleteMapping("/flows/{flowId}")
    public void deleteFlow(@PathVariable String flowId) {
        flowService.deleteFlow(flowId);
    }

    @PostMapping("/flows/{flowId}/execute")
    public FlowResultDTO executeFlow(@PathVariable String flowId,
                                      @RequestBody ExecuteRequestDTO request) {
        FlowDefinitionDTO flowDef = flowService.getFlow(flowId);
        return flowEngine.execute(flowId, flowDef, request.getInput());
    }

    @GetMapping("/executions/{executionId}")
    public FlowResultDTO getExecution(@PathVariable String executionId) {
        FlowExec exec = flowExecRepository.findById(executionId)
            .orElseThrow(() -> new RuntimeException("Execution not found: " + executionId));

        FlowResultDTO result = new FlowResultDTO();
        result.setExecutionId(exec.getId());
        result.setStatus(exec.getStatus());
        result.setTotalDurationMs(exec.getDurationMs() != null ? exec.getDurationMs().longValue() : 0L);
        return result;
    }

    @GetMapping("/executions/{executionId}/logs")
    public List<String> getExecutionLogs(@PathVariable String executionId) {
        return nodeExecRepository.findByExecId(executionId).stream()
            .map(ne -> String.format("[%s] %s: %s",
                ne.getStatus(),
                ne.getNodeId(),
                ne.getErrorMsg() != null ? ne.getErrorMsg() : "OK"))
            .toList();
    }

    @GetMapping("/executions/{executionId}/nodes/{nodeId}/runtime")
    public NodeResultDTO getNodeRuntime(@PathVariable String executionId,
                                         @PathVariable String nodeId) {
        return nodeExecRepository.findByExecId(executionId).stream()
            .filter(ne -> ne.getNodeId().equals(nodeId))
            .findFirst()
            .map(ne -> {
                NodeResultDTO dto = new NodeResultDTO();
                dto.setSuccess("SUCCESS".equals(ne.getStatus()));
                dto.setErrorMsg(ne.getErrorMsg());
                dto.setDurationMs(ne.getDurationMs() != null ? ne.getDurationMs().longValue() : 0L);
                return dto;
            })
            .orElseThrow(() -> new RuntimeException("Node execution not found"));
    }
}

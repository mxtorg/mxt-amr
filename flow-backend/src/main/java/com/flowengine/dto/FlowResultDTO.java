package com.flowengine.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowResultDTO {
    private String executionId;
    private String status;
    private Object output;
    private Map<String, NodeResultDTO> nodeResults;
    private Long totalDurationMs;
}

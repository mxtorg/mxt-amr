package com.flowengine.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeResultDTO {
    private Boolean success;
    private Object output;
    private String errorMsg;
    private Long durationMs;
}

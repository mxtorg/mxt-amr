package com.flowengine.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteRequestDTO {
    private Map<String, Object> input;
    private String mode;
}

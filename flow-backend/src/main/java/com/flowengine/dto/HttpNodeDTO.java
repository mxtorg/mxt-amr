package com.flowengine.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class HttpNodeDTO extends BaseNodeDTO {
    private String source;
    private Integer groupId;
    private String api;
    private String method;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private Integer price;
    private Integer timeout;
}

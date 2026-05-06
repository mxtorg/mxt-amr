package com.flowengine.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RouterNodeDTO extends BaseNodeDTO {
    private Map<String, Object> mappingSchema;
    private Map<String, Object> requestIntercept;
    private Map<String, Object> responseIntercept;
    private Map<String, Object> strategySchema;
}

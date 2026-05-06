package com.flowengine.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowDefinitionDTO {
    private String id;
    private String name;
    private String description;
    private Integer status;
    private Integer version;
    private List<Object> nodes;
    private List<EdgeDTO> edges;
}

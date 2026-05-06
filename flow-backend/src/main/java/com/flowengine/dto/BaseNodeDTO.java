package com.flowengine.dto;

import com.flowengine.domain.enums.NodeType;
import lombok.Data;
import java.util.Map;

@Data
public class BaseNodeDTO {
    private String id;
    private String flowId;
    private String[] pids;
    private String[] nids;
    private NodeType nodeType;
    private String name;
    private String description;
    private Map<String, Object> position;
}

package com.flowengine.core.executor;

import com.flowengine.core.context.FlowContext;
import com.flowengine.dto.NodeResultDTO;
import com.flowengine.domain.enums.NodeType;

public interface NodeExecutor {
    NodeType getType();
    NodeResultDTO execute(Object node, FlowContext context);
}

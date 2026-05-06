package com.flowengine.config;

import com.flowengine.core.executor.HttpExecutor;
import com.flowengine.core.executor.RouterExecutor;
import com.flowengine.core.executor.NodeExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class ExecutorConfig {

    @Bean
    public Map<String, NodeExecutor> executorMap(List<NodeExecutor> executors) {
        Map<String, NodeExecutor> map = new HashMap<>();
        for (NodeExecutor executor : executors) {
            map.put(executor.getType().name(), executor);
        }
        return map;
    }
}

package com.flowengine.core.executor;

import com.flowengine.core.context.FlowContext;
import com.flowengine.domain.enums.NodeType;
import com.flowengine.dto.HttpNodeDTO;
import com.flowengine.dto.NodeResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Slf4j
@Component
public class HttpExecutor implements NodeExecutor {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpExecutor() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public NodeType getType() {
        return NodeType.HTTP;
    }

    @Override
    public NodeResultDTO execute(Object node, FlowContext context) {
        long start = System.currentTimeMillis();
        try {
            HttpNodeDTO httpNode = objectMapper.convertValue(node, HttpNodeDTO.class);
            log.info("Executing HTTP node: {}, api: {}", httpNode.getId(), httpNode.getApi());

            Map<String, Object> params = (Map<String, Object>) context.resolveVariable(
                httpNode.getInputSchema() != null ? httpNode.getInputSchema() : new java.util.HashMap<>()
            );
            if (params == null) {
                params = (Map<String, Object>) context.getGlobalVars();
            }

            HttpMethod method = HttpMethod.valueOf(httpNode.getMethod());
            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(params, new org.springframework.http.HttpHeaders());

            ResponseEntity<String> response = restTemplate.exchange(
                httpNode.getApi(),
                method,
                entity,
                String.class
            );

            Object output = objectMapper.readValue(response.getBody(), Object.class);
            long duration = System.currentTimeMillis() - start;

            return new NodeResultDTO(true, output, null, duration);
        } catch (Exception e) {
            log.error("HTTP execution failed", e);
            long duration = System.currentTimeMillis() - start;
            return new NodeResultDTO(false, null, e.getMessage(), duration);
        }
    }
}

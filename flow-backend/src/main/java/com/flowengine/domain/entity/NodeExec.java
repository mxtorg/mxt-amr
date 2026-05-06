package com.flowengine.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "node_exec")
public class NodeExec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exec_id", length = 32, nullable = false)
    private String execId;

    @Column(name = "node_id", length = 32, nullable = false)
    private String nodeId;

    @Column(name = "node_type", length = 16, nullable = false)
    private String nodeType;

    @Column(length = 16)
    private String status = "RUNNING";

    @Column(name = "input_snapshot", columnDefinition = "TEXT")
    private String inputSnapshot;

    @Column(name = "output_snapshot", columnDefinition = "TEXT")
    private String outputSnapshot;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @PrePersist
    protected void onCreate() {
        startTime = LocalDateTime.now();
    }
}

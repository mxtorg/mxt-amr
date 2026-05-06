package com.flowengine.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "flow_exec")
public class FlowExec {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "flow_id", length = 32, nullable = false)
    private String flowId;

    @Column(name = "flow_version", nullable = false)
    private Integer flowVersion;

    @Column(length = 16)
    private String status = "RUNNING";

    @Column(name = "input_param", columnDefinition = "TEXT")
    private String inputParam;

    @Column(name = "output_result", columnDefinition = "TEXT")
    private String outputResult;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @PrePersist
    protected void onCreate() {
        startTime = LocalDateTime.now();
    }
}

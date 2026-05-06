package com.flowengine.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exec_log")
public class ExecLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exec_id", length = 32, nullable = false)
    private String execId;

    @Column(name = "node_id", length = 32)
    private String nodeId;

    @Column(name = "log_level", length = 8)
    private String logLevel = "INFO";

    @Column(name = "log_type", length = 16)
    private String logType = "SYSTEM";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}

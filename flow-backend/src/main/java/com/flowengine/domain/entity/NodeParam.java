package com.flowengine.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "node_param")
public class NodeParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_id", length = 32, nullable = false)
    private String nodeId;

    @Column(name = "param_key", length = 64, nullable = false)
    private String paramKey;

    @Column(columnDefinition = "TEXT")
    private String paramValue;

    @Column(name = "value_type", length = 16)
    private String valueType = "STRING";

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}

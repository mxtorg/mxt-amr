package com.flowengine.domain.entity;

import com.flowengine.domain.enums.NodeType;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "flow_def")
public class FlowDef {

    @Id
    @Column(length = 32)
    private String id;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(columnDefinition = "TEXT")
    private String dagJson;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(length = 64)
    private String createBy;

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

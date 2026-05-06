package com.flowengine.domain.entity;

import com.flowengine.domain.enums.NodeType;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "node_def")
public class NodeDef {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "flow_id", length = 32, nullable = false)
    private String flowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", length = 16, nullable = false)
    private NodeType nodeType;

    @Column(name = "node_name", length = 128, nullable = false)
    private String nodeName;

    @Column(length = 512)
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal posX;

    @Column(precision = 10, scale = 2)
    private BigDecimal posY;

    @Column(length = 512)
    private String pids;

    @Column(length = 512)
    private String nids;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

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

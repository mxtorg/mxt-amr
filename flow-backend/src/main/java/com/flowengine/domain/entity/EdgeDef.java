package com.flowengine.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "edge_def")
public class EdgeDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_id", length = 32, nullable = false)
    private String flowId;

    @Column(name = "from_node", length = 32, nullable = false)
    private String fromNode;

    @Column(name = "to_node", length = 32, nullable = false)
    private String toNode;

    @Column(length = 512)
    private String condition;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}

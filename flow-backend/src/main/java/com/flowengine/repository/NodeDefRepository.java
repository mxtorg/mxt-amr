package com.flowengine.repository;

import com.flowengine.domain.entity.NodeDef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NodeDefRepository extends JpaRepository<NodeDef, String> {
    List<NodeDef> findByFlowId(String flowId);
    void deleteByFlowId(String flowId);
}

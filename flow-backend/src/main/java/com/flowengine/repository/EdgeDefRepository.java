package com.flowengine.repository;

import com.flowengine.domain.entity.EdgeDef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EdgeDefRepository extends JpaRepository<EdgeDef, Long> {
    List<EdgeDef> findByFlowId(String flowId);
    void deleteByFlowId(String flowId);
}

package com.flowengine.repository;

import com.flowengine.domain.entity.NodeParam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NodeParamRepository extends JpaRepository<NodeParam, Long> {
    List<NodeParam> findByNodeId(String nodeId);
    void deleteByNodeId(String nodeId);
}

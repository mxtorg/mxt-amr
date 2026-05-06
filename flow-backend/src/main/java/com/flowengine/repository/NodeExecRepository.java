package com.flowengine.repository;

import com.flowengine.domain.entity.NodeExec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NodeExecRepository extends JpaRepository<NodeExec, Long> {
    List<NodeExec> findByExecId(String execId);
}

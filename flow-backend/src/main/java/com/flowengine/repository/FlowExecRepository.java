package com.flowengine.repository;

import com.flowengine.domain.entity.FlowExec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FlowExecRepository extends JpaRepository<FlowExec, String> {
    List<FlowExec> findByFlowIdOrderByStartTimeDesc(String flowId);
}

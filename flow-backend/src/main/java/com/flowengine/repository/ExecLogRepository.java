package com.flowengine.repository;

import com.flowengine.domain.entity.ExecLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExecLogRepository extends JpaRepository<ExecLog, Long> {
    List<ExecLog> findByExecIdOrderByCreateTimeAsc(String execId);
}

package com.dehui.property.modules.workorder.repository;

import com.dehui.property.modules.workorder.entity.WorkOrderLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderLogRepository extends JpaRepository<WorkOrderLog, Long> {
    List<WorkOrderLog> findByWorkOrderIdOrderByCreatedTimeAsc(Long workOrderId);
}

package com.dehui.property.modules.workorder.repository;

import com.dehui.property.modules.workorder.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    boolean existsByOrderNumber(String orderNumber);

    List<WorkOrder> findByMobileUserIdOrderByCreatedTimeDesc(Long mobileUserId);

    long countByMobileUserId(Long mobileUserId);

    long countByOrderNumberStartingWith(String prefix);

    @Query(value = "SELECT COUNT(*) FROM work_order WHERE status = :status", nativeQuery = true)
    Long countByStatus(@Param("status") String status);

    @Query(value = "SELECT COUNT(*) FROM work_order WHERE priority = :priority", nativeQuery = true)
    Long countByPriority(@Param("priority") String priority);
}

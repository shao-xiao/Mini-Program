package com.dehui.property.modules.parking.repository;

import com.dehui.property.modules.parking.entity.ParkingOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParkingOperationLogRepository extends JpaRepository<ParkingOperationLog, Long> {
    List<ParkingOperationLog> findByTargetTypeAndTargetIdOrderByCreatedTimeDesc(String targetType, Long targetId);
}

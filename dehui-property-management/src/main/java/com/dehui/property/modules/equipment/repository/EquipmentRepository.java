package com.dehui.property.modules.equipment.repository;

import com.dehui.property.modules.equipment.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    @Query(value = "SELECT COUNT(*) FROM equipment WHERE status = :status", nativeQuery = true)
    Long countByStatus(@Param("status") String status);
}

package com.dehui.property.modules.parking.repository;

import com.dehui.property.modules.parking.entity.ParkingBill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParkingBillRepository extends JpaRepository<ParkingBill, Long> {

    List<ParkingBill> findByTenantId(Long tenantId);

    List<ParkingBill> findByParkingSpaceId(Long parkingSpaceId);

    List<ParkingBill> findByStatus(String status);

    Long countByStatus(String status);
}
package com.dehui.property.modules.parking.repository;

import com.dehui.property.modules.parking.entity.ParkingBill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ParkingBillRepository extends JpaRepository<ParkingBill, Long> {

    List<ParkingBill> findByTenantId(Long tenantId);

    List<ParkingBill> findByParkingSpaceId(Long parkingSpaceId);

    List<ParkingBill> findByStatus(String status);

    List<ParkingBill> findByPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByCreatedTimeDesc(LocalDate periodStart, LocalDate periodEnd);

    List<ParkingBill> findBySyncStatusInAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(List<String> statuses, LocalDate periodStart, LocalDate periodEnd);

    boolean existsByAssignmentIdAndPeriodStartAndPeriodEndAndBillType(Long assignmentId, LocalDate periodStart, LocalDate periodEnd, String billType);

    Optional<ParkingBill> findByAssignmentIdAndPeriodStartAndPeriodEndAndBillType(Long assignmentId, LocalDate periodStart, LocalDate periodEnd, String billType);

    long countByParkingSpaceId(Long parkingSpaceId);

    long countBySpaceId(Long spaceId);

    Long countByStatus(String status);
}

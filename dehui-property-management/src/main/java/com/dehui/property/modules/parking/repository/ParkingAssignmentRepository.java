package com.dehui.property.modules.parking.repository;

import com.dehui.property.modules.parking.entity.ParkingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParkingAssignmentRepository extends JpaRepository<ParkingAssignment, Long> {
    Optional<ParkingAssignment> findFirstBySpaceIdAndStatus(Long spaceId, String status);

    List<ParkingAssignment> findBySpaceIdOrderByCreatedTimeDesc(Long spaceId);

    List<ParkingAssignment> findByStatus(String status);

    long countBySpaceId(Long spaceId);
}

package com.dehui.property.modules.parking.repository;

import com.dehui.property.modules.parking.entity.ParkingSpace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {

    Optional<ParkingSpace> findBySpaceCode(String spaceCode);

    List<ParkingSpace> findByStatus(String status);

    Long countByStatus(String status);
}
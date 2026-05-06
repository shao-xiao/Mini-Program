package com.dehui.property.modules.building.repository;

import com.dehui.property.modules.building.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FloorRepository extends JpaRepository<Floor, Long> {
    List<Floor> findByBuildingIdOrderByFloorNumberAsc(Long buildingId);

    @Query(value = "SELECT COUNT(*) FROM room WHERE floor_id = :floorId", nativeQuery = true)
    Long countRoomsByFloorId(@Param("floorId") Long floorId);

    @Query(value = "SELECT COALESCE(SUM(area), 0) FROM room WHERE floor_id = :floorId", nativeQuery = true)
    Double sumRoomAreaByFloorId(@Param("floorId") Long floorId);

    @Query(value = "SELECT COUNT(*) FROM room WHERE floor_id = :floorId AND status = :status", nativeQuery = true)
    Long countRoomsByFloorIdAndStatus(@Param("floorId") Long floorId, @Param("status") String status);
}

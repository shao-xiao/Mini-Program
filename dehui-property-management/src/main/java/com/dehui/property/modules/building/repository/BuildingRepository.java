package com.dehui.property.modules.building.repository;

import com.dehui.property.modules.building.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    @Query(value = "SELECT COUNT(*) FROM floor WHERE building_id = :buildingId", nativeQuery = true)
    Long countFloorsByBuildingId(@Param("buildingId") Long buildingId);

    @Query(value = "SELECT COALESCE(SUM(total_area), 0) FROM floor WHERE building_id = :buildingId", nativeQuery = true)
    Double sumFloorAreaByBuildingId(@Param("buildingId") Long buildingId);

    @Query(value = "SELECT COUNT(*) FROM room r JOIN floor f ON r.floor_id = f.id WHERE f.building_id = :buildingId", nativeQuery = true)
    Long countRoomsByBuildingId(@Param("buildingId") Long buildingId);

    @Query(value = "SELECT COUNT(*) FROM room r JOIN floor f ON r.floor_id = f.id WHERE f.building_id = :buildingId AND r.status = :status", nativeQuery = true)
    Long countRoomsByBuildingIdAndStatus(@Param("buildingId") Long buildingId, @Param("status") String status);

    @Query(value = "SELECT COALESCE(SUM(r.area), 0) FROM room r JOIN floor f ON r.floor_id = f.id WHERE f.building_id = :buildingId", nativeQuery = true)
    Double sumRoomAreaByBuildingId(@Param("buildingId") Long buildingId);
}

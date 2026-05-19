package com.dehui.property.modules.building.repository;

import com.dehui.property.modules.building.entity.Floor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FloorRepository extends JpaRepository<Floor, Long> {
    List<Floor> findByBuildingIdOrderByFloorNumberAsc(Long buildingId);

    List<Floor> findByBuildingIdAndDeletedAtIsNullOrderBySortOrderAscFloorNumberAsc(Long buildingId);

    @EntityGraph(attributePaths = "building")
    @Query("select f from Floor f where f.id = :id")
    Optional<Floor> findWithBuildingById(@Param("id") Long id);

    @Query(value = "SELECT COUNT(*) FROM room WHERE floor_id = :floorId AND deleted_at IS NULL", nativeQuery = true)
    Long countRoomsByFloorId(@Param("floorId") Long floorId);

    @Query(value = "SELECT COALESCE(SUM(area), 0) FROM room WHERE floor_id = :floorId AND deleted_at IS NULL", nativeQuery = true)
    Double sumRoomAreaByFloorId(@Param("floorId") Long floorId);

    @Query(value = "SELECT COUNT(*) FROM room WHERE floor_id = :floorId AND status = :status AND deleted_at IS NULL", nativeQuery = true)
    Long countRoomsByFloorIdAndStatus(@Param("floorId") Long floorId, @Param("status") String status);
}

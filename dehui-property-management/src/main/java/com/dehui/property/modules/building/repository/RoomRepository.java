package com.dehui.property.modules.building.repository;

import com.dehui.property.modules.building.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByFloorIdOrderByRoomNumberAsc(Long floorId);

    @Query(value = "SELECT COUNT(*) FROM room WHERE floor_id = :floorId AND status = :status", nativeQuery = true)
    Long countByFloorIdAndStatus(@Param("floorId") Long floorId, @Param("status") String status);

    @Query(value = "SELECT COUNT(*) FROM room WHERE status = :status", nativeQuery = true)
    Long countByStatus(@Param("status") String status);
}

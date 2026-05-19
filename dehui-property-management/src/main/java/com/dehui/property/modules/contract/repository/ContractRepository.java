package com.dehui.property.modules.contract.repository;

import com.dehui.property.modules.contract.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByLeaseId(Long leaseId);

    boolean existsByLeaseId(Long leaseId);

    boolean existsByContractNumber(String contractNumber);

    List<Contract> findByStatus(String status);

    List<Contract> findByStatusAndLeaseIdIsNull(String status);

    List<Contract> findByRoomIdAndStatus(Long roomId, String status);

    List<Contract> findByStatusIn(List<String> statuses);

    List<Contract> findByTenantIdOrderByCreatedTimeDesc(Long tenantId);

    @Query(value = "SELECT COUNT(*) FROM contract WHERE status = :status", nativeQuery = true)
    Long countByStatus(@Param("status") String status);

    @Query(value = """
            SELECT COUNT(DISTINCT c.room_id)
            FROM contract c
            JOIN room r ON c.room_id = r.id
            WHERE c.status = 'ACTIVE'
              AND c.room_id IS NOT NULL
              AND c.start_date <= :today
              AND c.end_date >= :today
              AND r.deleted_at IS NULL
              AND r.status IN ('AVAILABLE', 'RENTED', 'RESERVED', 'VACANT', 'LEASED')
            """, nativeQuery = true)
    Long countActiveLeasedRoomIds(@Param("today") java.time.LocalDate today);

    @Query(value = """
            SELECT COUNT(DISTINCT c.room_id)
            FROM contract c
            JOIN room r ON c.room_id = r.id
            LEFT JOIN floor f ON r.floor_id = f.id
            WHERE c.status = 'ACTIVE'
              AND c.room_id IS NOT NULL
              AND c.start_date <= :today
              AND c.end_date >= :today
              AND r.deleted_at IS NULL
              AND (r.building_id = :buildingId OR f.building_id = :buildingId)
              AND r.status IN ('AVAILABLE', 'RENTED', 'RESERVED', 'VACANT', 'LEASED')
            """, nativeQuery = true)
    Long countActiveLeasedRoomIdsByBuildingId(@Param("today") java.time.LocalDate today,
                                              @Param("buildingId") Long buildingId);
}

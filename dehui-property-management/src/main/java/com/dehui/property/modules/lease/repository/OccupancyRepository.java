package com.dehui.property.modules.lease.repository;

import com.dehui.property.modules.lease.entity.Occupancy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OccupancyRepository extends JpaRepository<Occupancy, Long> {
    Optional<Occupancy> findByContractId(Long contractId);

    Optional<Occupancy> findByContractIdAndStatus(Long contractId, String status);

    List<Occupancy> findByRoomIdAndStatusOrderByCheckInDateDesc(Long roomId, String status);

    List<Occupancy> findByTenantIdOrderByCheckInDateDesc(Long tenantId);
}

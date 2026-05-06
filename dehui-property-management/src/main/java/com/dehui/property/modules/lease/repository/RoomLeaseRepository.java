package com.dehui.property.modules.lease.repository;

import com.dehui.property.modules.lease.entity.RoomLease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomLeaseRepository extends JpaRepository<RoomLease, Long> {
    List<RoomLease> findByRoomId(Long roomId);

    List<RoomLease> findByTenantId(Long tenantId);

    Optional<RoomLease> findByRoomIdAndStatus(Long roomId, String status);

    List<RoomLease> findByRoomIdAndStatusOrderByStartDateDesc(Long roomId, String status);

    List<RoomLease> findByTenantIdOrderByStartDateDesc(Long tenantId);
}
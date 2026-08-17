package com.dehui.property.modules.lease.dto;

import com.dehui.property.modules.lease.domain.model.LeaseStatus;
import com.dehui.property.modules.lease.domain.model.LeaseRoomStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record LeaseResponse(
        Long id,
        String leaseNo,
        Long tenantId,
        String tenantName,
        Long contractId,
        String contractNo,
        LeaseStatus status,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        LocalDateTime actualCheckinAt,
        LocalDateTime actualCheckoutAt,
        String checkoutReason,
        Long renewalOfLeaseId,
        long version,
        int roomCount,
        int activeRoomCount,
        int terminatedRoomCount,
        List<Long> roomIds,
        List<String> roomNames,
        List<LeaseRoomStatus> roomStatuses,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Map<String, Object> customFields
) {
    public LeaseResponse(
            Long id,
            String leaseNo,
            Long tenantId,
            String tenantName,
            Long contractId,
            String contractNo,
            LeaseStatus status,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDateTime actualCheckinAt,
            LocalDateTime actualCheckoutAt,
            String checkoutReason,
            Long renewalOfLeaseId,
            long version,
            int roomCount,
            int activeRoomCount,
            int terminatedRoomCount,
            List<Long> roomIds,
            List<String> roomNames,
            List<LeaseRoomStatus> roomStatuses,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id, leaseNo, tenantId, tenantName, contractId, contractNo, status,
                plannedStartDate, plannedEndDate, actualCheckinAt, actualCheckoutAt,
                checkoutReason, renewalOfLeaseId, version, roomCount, activeRoomCount,
                terminatedRoomCount, roomIds, roomNames, roomStatuses, createdAt, updatedAt,
                Map.of()
        );
    }

    public LeaseResponse withCustomFields(Map<String, Object> values) {
        return new LeaseResponse(
                id, leaseNo, tenantId, tenantName, contractId, contractNo, status,
                plannedStartDate, plannedEndDate, actualCheckinAt, actualCheckoutAt,
                checkoutReason, renewalOfLeaseId, version, roomCount, activeRoomCount,
                terminatedRoomCount, roomIds, roomNames, roomStatuses, createdAt, updatedAt,
                values == null ? Map.of() : Map.copyOf(values)
        );
    }
}

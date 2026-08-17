package com.dehui.property.modules.contract.dto;

import java.time.LocalDateTime;

public record ContractHistoryResponse(
        String sourceType,
        Long sourceId,
        Long leaseId,
        String leaseNo,
        String fromStatus,
        String toStatus,
        String reason,
        Long operatorId,
        String operatorName,
        LocalDateTime occurredAt
) {
}

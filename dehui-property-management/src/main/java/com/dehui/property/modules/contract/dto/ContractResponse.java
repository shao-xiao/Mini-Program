package com.dehui.property.modules.contract.dto;

import com.dehui.property.modules.contract.domain.model.ContractStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ContractResponse(
        Long id,
        String contractNo,
        Long tenantId,
        String tenantName,
        ContractStatus status,
        LocalDate effectiveDate,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime signedAt,
        String currency,
        int currentRevisionNo,
        Long renewalOfContractId,
        long version,
        List<Long> roomIds,
        List<String> roomNames,
        BigDecimal rentAmount,
        BigDecimal depositAmount,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

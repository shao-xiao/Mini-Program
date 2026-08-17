package com.dehui.property.modules.lease.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record LeaseCreateRequest(
        @Size(max = 64) String leaseNo,
        @NotNull Long contractId,
        Long tenantId,
        List<@NotNull Long> roomIds,
        @NotNull LocalDate plannedStartDate,
        @NotNull LocalDate plannedEndDate,
        @Size(max = 500) String remark
) {
}

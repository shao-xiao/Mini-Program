package com.dehui.property.modules.lease.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RoomLeaseCreateRequest {
    private Long contractId;

    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    private LocalDate endDate;

    private String remark;
}

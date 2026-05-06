package com.dehui.property.modules.workorder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderAssignRequest {
    @NotNull(message = "处理人ID不能为空")
    private Long handlerId;
}
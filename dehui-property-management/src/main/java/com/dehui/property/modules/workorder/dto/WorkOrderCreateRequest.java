package com.dehui.property.modules.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderCreateRequest {
    @NotBlank(message = "工单标题不能为空")
    private String title;

    private String description;

    private Long equipmentId;

    private String location;

    /**
     * 工单类型：REPAIR / PATROL / CLEAN / SECURITY
     * 可为空，后端默认 REPAIR
     */
    private String orderType;

    @NotBlank(message = "工单类别不能为空")
    private String category;

    @NotBlank(message = "优先级不能为空")
    private String priority;

    @NotNull(message = "报修人ID不能为空")
    private Long reporterId;
}
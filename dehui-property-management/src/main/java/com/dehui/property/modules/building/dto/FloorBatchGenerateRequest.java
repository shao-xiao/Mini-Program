package com.dehui.property.modules.building.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FloorBatchGenerateRequest {
    @NotNull(message = "楼宇ID不能为空")
    private Long buildingId;

    private Integer basementStart = 2;

    private Integer basementEnd = 1;

    private Integer aboveStart = 1;

    private Integer aboveEnd = 9;

    private Double totalArea;

    private String status;
}

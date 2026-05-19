package com.dehui.property.modules.building.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FloorUpdateRequest {
    private Long buildingId;

    @NotNull(message = "楼层号不能为空")
    private Integer floorNumber;

    private String floorName;

    private Integer sortOrder;

    private Double totalArea;

    private String description;

    private String status;
}

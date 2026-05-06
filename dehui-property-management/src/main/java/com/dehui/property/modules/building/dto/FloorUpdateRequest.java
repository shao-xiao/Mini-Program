package com.dehui.property.modules.building.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FloorUpdateRequest {
    @NotNull(message = "楼层号不能为空")
    private Integer floorNumber;
    private String floorName;
    private Double totalArea;
}

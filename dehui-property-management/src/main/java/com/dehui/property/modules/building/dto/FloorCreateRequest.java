package com.dehui.property.modules.building.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FloorCreateRequest {
    @NotNull(message = "楼层号不能为空")
    private Integer floorNumber;
    private String floorName;
    @Min(value = 0, message = "总面积不能为负数")
    private Double totalArea;
}

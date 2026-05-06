package com.dehui.property.modules.building.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BuildingUpdateRequest {
    @NotBlank(message = "楼栋名称不能为空")
    private String buildingName;
    private String buildingCode;
    private String address;
    private Integer totalFloors;
    private String description;
    private String status;
}

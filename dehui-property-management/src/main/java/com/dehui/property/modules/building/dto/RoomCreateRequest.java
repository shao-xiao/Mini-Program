package com.dehui.property.modules.building.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoomCreateRequest {
    private Long buildingId;

    private Long floorId;

    @NotBlank(message = "房间号不能为空")
    private String roomNumber;

    private String roomName;

    @Min(value = 0, message = "面积不能为负数")
    private Double area;

    private String roomType;

    private String status;

    private String description;
}

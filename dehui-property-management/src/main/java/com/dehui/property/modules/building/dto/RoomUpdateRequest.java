package com.dehui.property.modules.building.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoomUpdateRequest {
    private Long buildingId;

    private Long floorId;

    @NotBlank(message = "房间号不能为空")
    private String roomNumber;

    private String roomName;

    private Double area;

    private String roomType;

    private String status;

    private String description;
}

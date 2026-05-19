package com.dehui.property.modules.building.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;


import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {
    private Long id;
    private Long buildingId;
    private String buildingName;
    private Long floorId;
    private String floorName;
    private Integer floorNumber;
    private String roomNumber;
    private String roomName;
    private Double area;
    private String roomType;
    private String roomTypeText;
    private String status;
    private String statusText;
    private String description;
    private LocalDateTime deletedAt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

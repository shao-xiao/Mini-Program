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
    private Long floorId;
    private String roomNumber;
    private Double area;
    private String roomType;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

package com.dehui.property.modules.building.dto;

import lombok.Builder;
import lombok.Data;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingResponse {
    private Long id;
    private String buildingName;
    private String buildingCode;
    private String address;
    private Integer totalFloors;
    private String description;
    private String status;
    private LocalDateTime deletedAt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

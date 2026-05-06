package com.dehui.property.modules.building.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BuildingStatsResponse {
    private Long buildingId;
    private String buildingName;
    private Long floorCount;
    private Long roomCount;
    private Double totalArea;
    private Long availableCount;
    private Long rentedCount;
    private Long maintenanceCount;
    private Long reservedCount;
    private Long disabledCount;
    private Double rentalRate;
    private Double vacancyRate;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
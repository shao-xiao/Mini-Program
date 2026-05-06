package com.dehui.property.modules.building.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FloorStatsResponse {
    private Long floorId;
    private Long buildingId;
    private Integer floorNumber;
    private String floorName;
    private Long roomCount;
    private Double totalArea;
    private Long availableCount;
    private Long rentedCount;
    private Long maintenanceCount;
    private Long reservedCount;
    private Long disabledCount;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
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
public class FloorResponse {
    private Long id;
    private Long buildingId;
    private Integer floorNumber;
    private String floorName;
    private Integer sortOrder;
    private Double totalArea;
    private String description;
    private String status;
    private LocalDateTime deletedAt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

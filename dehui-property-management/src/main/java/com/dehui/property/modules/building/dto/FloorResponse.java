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
    private Double totalArea;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

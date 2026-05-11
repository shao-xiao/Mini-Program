package com.dehui.property.modules.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InvestmentRoomResponse {
    private Long id;
    private String roomNumber;
    private Double area;
    private String roomType;
    private String buildingName;
    private Integer floorNumber;
    private String floorName;
    private String statusText;
}

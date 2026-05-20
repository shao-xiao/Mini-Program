package com.dehui.property.modules.parking.dto;

import lombok.Data;

@Data
public class ParkingSpaceRequest {
    private String spaceNo;
    private String spaceCode;
    private String area;
    private String floor;
    private String type;
    private String spaceType;
    private String status;
    private String remark;
    private Integer sortOrder;
}

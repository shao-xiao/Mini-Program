package com.dehui.property.modules.parking.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ParkingReleaseRequest {
    private LocalDate endDate;
    private String operatorName;
    private String remark;
}

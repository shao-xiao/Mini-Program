package com.dehui.property.modules.parking.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ParkingBillSyncRequest {
    private String month;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}

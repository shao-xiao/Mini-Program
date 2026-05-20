package com.dehui.property.modules.energy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EnergyLastReadingResponse {
    private Long meterId;
    private String meterNo;
    private String meterType;
    private String unit;
    private BigDecimal multiplier;
    private Long buildingId;
    private String buildingName;
    private Long floorId;
    private String floorName;
    private Long roomId;
    private String roomName;
    private Long tenantId;
    private String tenantName;
    private String installLocation;
    private String lastPeriodMonth;
    private LocalDate lastReadingDate;
    private BigDecimal previousReading;
}

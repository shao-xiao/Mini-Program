package com.dehui.property.modules.energy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EnergyMeterResponse {
    private Long id;
    private String meterNo;
    private String meterType;
    private Long buildingId;
    private String buildingName;
    private Long floorId;
    private String floorName;
    private Long roomId;
    private String roomName;
    private Long tenantId;
    private String tenantName;
    private String installLocation;
    private String unit;
    private BigDecimal multiplier;
    private String billingMode;
    private String status;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

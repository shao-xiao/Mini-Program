package com.dehui.property.modules.energy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EnergyReadingResponse {
    private Long id;
    private Long meterId;
    private String meterNo;
    private String meterType;
    private LocalDate readingDate;
    private String periodMonth;
    private BigDecimal previousReading;
    private BigDecimal currentReading;
    private BigDecimal usageAmount;
    private BigDecimal multiplier;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal settlementAmount;
    private Long buildingId;
    private String buildingName;
    private Long floorId;
    private String floorName;
    private Long roomId;
    private String roomName;
    private Long tenantId;
    private String tenantName;
    private String billStatus;
    private Long billId;
    private Boolean abnormalFlag;
    private String abnormalReason;
    private String abnormalStatus;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

package com.dehui.property.modules.energy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EnergyBillResponse {
    private Long id;
    private String billNumber;
    private String billType;
    private String title;
    private Long tenantId;
    private Long roomId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private String status;
    private String auditStatus;
    private Long sourceId;
    private String remark;
    private LocalDateTime createdTime;
}

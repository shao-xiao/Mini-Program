package com.dehui.property.modules.feerule.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FeeRuleResponse {

    private Long id;
    private String ruleName;
    private Long tenantId;
    private Long contractId;
    private String feeType;
    private BigDecimal amount;
    private String cycle;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer generateDay;
    private String status;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
package com.dehui.property.modules.feerule.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FeeRuleCreateRequest {

    private String ruleName;

    private Long tenantId;

    private Long contractId;

    private String feeType;

    private BigDecimal amount;

    private String cycle;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer generateDay;

    private String remark;
}
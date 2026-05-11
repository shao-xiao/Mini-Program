package com.dehui.property.modules.contract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ContractResponse {
    private Long id;
    private String contractNumber;
    private String contractName;
    private Long tenantId;
    private Long roomId;
    private Long leaseId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal rentAmount;
    private BigDecimal propertyFeeAmount;
    private BigDecimal depositAmount;
    private String paymentCycle;
    private Integer billingDay;
    private Integer dueDay;
    private String paymentTerms;
    private String billingRule;
    private String status;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

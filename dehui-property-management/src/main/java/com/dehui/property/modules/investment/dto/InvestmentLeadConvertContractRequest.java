package com.dehui.property.modules.investment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvestmentLeadConvertContractRequest {
    private String contractNumber;
    private String contractName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal rentAmount;
    private BigDecimal propertyFeeAmount;
    private BigDecimal depositAmount;
    private String paymentCycle;
    private Integer advanceBillDays;
    private String remark;
}

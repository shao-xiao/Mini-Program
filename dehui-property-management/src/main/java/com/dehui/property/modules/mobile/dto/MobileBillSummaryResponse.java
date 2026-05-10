package com.dehui.property.modules.mobile.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MobileBillSummaryResponse {
    private BigDecimal totalAmount;
    private BigDecimal unpaidAmount;
    private BigDecimal overdueAmount;
    private BigDecimal paidAmount;
    private long totalCount;
    private long unpaidCount;
    private long overdueCount;
    private long paidCount;
}

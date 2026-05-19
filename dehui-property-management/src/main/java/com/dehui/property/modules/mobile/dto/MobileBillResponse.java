package com.dehui.property.modules.mobile.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MobileBillResponse {
    private Long id;
    private String billNumber;
    private String billType;
    private String billTypeText;
    private String title;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private BigDecimal unpaidAmount;
    private LocalDate dueDate;
    private String status;
    private String statusText;
    private Boolean overdue;
    private String sourceType;
    private String sourceTypeText;
    private String invoiceStatus;
    private String invoiceFileName;
    private String invoiceDownloadUrl;
    private String remark;
    private LocalDateTime createdTime;
}

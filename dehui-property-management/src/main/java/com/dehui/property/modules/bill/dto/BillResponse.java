package com.dehui.property.modules.bill.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BillResponse {
    private Long id;
    private String billNumber;
    private String billNo;
    private Long tenantId;
    private String tenantName;
    private Long contractId;
    private Long roomId;
    private String contractNumber;
    private String contractName;
    private String billType;
    private String billTypeText;
    private String title;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private BigDecimal unpaidAmount;
    private LocalDate dueDate;
    private Integer overdueDays;
    private BigDecimal lateFee;
    private String status;
    private String statusText;
    private String auditStatus;
    private String auditStatusText;
    private String auditRemark;
    private String approvedBy;
    private LocalDateTime approvedTime;
    private String sourceType;
    private String sourceTypeText;
    private Long sourceId;
    private String invoiceStatus;
    private String invoiceStatusText;
    private String invoiceFileName;
    private String invoiceDownloadUrl;
    private LocalDateTime invoiceUploadedAt;
    private String invoiceUploadedBy;
    private String remark;
    private Boolean overdue;
    private LocalDateTime paidTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

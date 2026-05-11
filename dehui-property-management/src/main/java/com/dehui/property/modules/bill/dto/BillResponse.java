package com.dehui.property.modules.bill.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BillResponse {
    private Long id;
    private String billNumber;
    private Long tenantId;
    private String tenantName;
    private Long contractId;
    private String contractNumber;
    private String contractName;
    private String billType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private LocalDate dueDate;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

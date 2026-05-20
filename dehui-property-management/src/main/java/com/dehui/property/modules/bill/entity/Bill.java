package com.dehui.property.modules.bill.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "bill")
public class Bill extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String billNumber;

    private Long tenantId;

    private String payerName;

    private Long contractId;

    private Long roomId;

    @Column(nullable = false)
    private String billType;

    private String title;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private BigDecimal amount;

    private BigDecimal paidAmount;

    private LocalDateTime paidTime;

    private LocalDate dueDate;

    private String status;

    private String auditStatus;

    private String auditRemark;

    private String approvedBy;

    private LocalDateTime approvedTime;

    private String sourceType;

    private Long sourceId;

    private String invoiceStatus;

    private String invoiceFilePath;

    private String invoiceFileName;

    private LocalDateTime invoiceUploadedAt;

    private String invoiceUploadedBy;

    private String remark;
}

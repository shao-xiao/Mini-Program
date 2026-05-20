package com.dehui.property.modules.parking.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ParkingBillResponse {
    private Long id;
    private String billNo;
    private String billNumber;
    private Long assignmentId;
    private Long spaceId;
    private Long parkingSpaceId;
    private String spaceNoSnapshot;
    private Long tenantId;
    private String partyType;
    private String partyTypeText;
    private Long partyId;
    private String partyNameSnapshot;
    private String plateNoSnapshot;
    private String plateNumber;
    private String billType;
    private String billTypeText;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private String status;
    private String statusText;
    private String syncStatus;
    private String syncStatusText;
    private Long financeBillId;
    private Long billId;
    private String syncError;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private LocalDateTime paidAt;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

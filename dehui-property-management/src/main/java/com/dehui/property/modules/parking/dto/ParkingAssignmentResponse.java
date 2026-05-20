package com.dehui.property.modules.parking.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ParkingAssignmentResponse {
    private Long id;
    private Long spaceId;
    private String spaceNo;
    private String partyType;
    private String partyTypeText;
    private Long partyId;
    private Long tenantId;
    private String partyNameSnapshot;
    private String plateNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyFee;
    private String billingType;
    private String billingTypeText;
    private String status;
    private String statusText;
    private String createdBy;
    private String releasedBy;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

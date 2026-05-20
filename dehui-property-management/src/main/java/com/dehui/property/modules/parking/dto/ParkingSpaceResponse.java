package com.dehui.property.modules.parking.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParkingSpaceResponse {
    private Long id;
    private String spaceNo;
    private String spaceCode;
    private String area;
    private String floor;
    private String type;
    private String spaceType;
    private String typeText;
    private String status;
    private String statusText;
    private String remark;
    private Integer sortOrder;
    private Long tenantId;
    private String plateNumber;
    private String partyType;
    private String partyTypeText;
    private Long partyId;
    private String partyNameSnapshot;
    private String plateNo;
    private java.math.BigDecimal monthlyFee;
    private String billingType;
    private ParkingAssignmentResponse activeAssignment;
    private LocalDateTime deletedAt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

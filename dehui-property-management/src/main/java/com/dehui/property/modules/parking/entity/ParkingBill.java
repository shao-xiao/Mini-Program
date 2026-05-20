package com.dehui.property.modules.parking.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "parking_bill")
@EqualsAndHashCode(callSuper = true)
public class ParkingBill extends BaseEntity {

    private String billNumber;

    private String billNo;

    private Long assignmentId;

    private Long parkingSpaceId;

    private Long spaceId;

    private String spaceNoSnapshot;

    private Long tenantId;

    private Boolean vip;

    private String plateNumber;

    private String partyType;

    private Long partyId;

    private String partyNameSnapshot;

    private String plateNoSnapshot;

    private String billType; // MONTHLY / TEMP

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private BigDecimal amount;

    private String status; // UNPAID / PAID / CANCELLED

    private String syncStatus;

    private Long financeBillId;

    private String syncError;

    private Long billId;

    private LocalDate dueDate;

    private LocalDate paidDate;

    private LocalDateTime paidAt;

    private String remark;
}

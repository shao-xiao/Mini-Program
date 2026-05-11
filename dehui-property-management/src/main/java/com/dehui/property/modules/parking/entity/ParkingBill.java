package com.dehui.property.modules.parking.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "parking_bill")
@EqualsAndHashCode(callSuper = true)
public class ParkingBill extends BaseEntity {

    private String billNumber;

    private Long parkingSpaceId;

    private Long tenantId;

    private Boolean vip;

    private String plateNumber;

    private String billType; // MONTHLY / TEMP

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private BigDecimal amount;

    private String status; // UNPAID / PAID / CANCELLED

    private Long billId;

    private LocalDate dueDate;

    private LocalDate paidDate;

    private String remark;
}

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
@Table(name = "parking_assignment")
@EqualsAndHashCode(callSuper = true)
public class ParkingAssignment extends BaseEntity {
    private Long spaceId;

    private String partyType;

    private Long partyId;

    private String partyNameSnapshot;

    private String plateNo;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal monthlyFee;

    private String billingType;

    private String status;

    private String createdBy;

    private String releasedBy;

    private String remark;
}

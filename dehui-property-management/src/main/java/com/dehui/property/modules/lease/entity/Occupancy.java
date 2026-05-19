package com.dehui.property.modules.lease.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "occupancy")
public class Occupancy extends BaseEntity {
    @Column(nullable = false, unique = true)
    private Long contractId;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private LocalDate checkInDate;

    private LocalDate plannedEndDate;

    private LocalDate checkoutDate;

    @Column(nullable = false)
    private String status;

    private String remark;
}

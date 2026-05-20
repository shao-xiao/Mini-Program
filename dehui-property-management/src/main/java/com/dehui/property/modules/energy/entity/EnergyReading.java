package com.dehui.property.modules.energy.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "energy_reading")
public class EnergyReading extends BaseEntity {
    @Column(nullable = false)
    private Long meterId;

    @Column(nullable = false)
    private String meterNo;

    @Column(nullable = false)
    private String meterType;

    private LocalDate readingDate;

    @Column(nullable = false)
    private String periodMonth;

    private BigDecimal previousReading;

    private BigDecimal currentReading;

    private BigDecimal usageAmount;

    private BigDecimal unitPrice;

    private BigDecimal settlementAmount;

    private BigDecimal multiplier;

    private String unit;

    private Long buildingId;

    private String buildingName;

    private Long floorId;

    private String floorName;

    private Long roomId;

    private String roomName;

    private Long tenantId;

    private String tenantName;

    private String billStatus;

    private Long billId;

    private Boolean abnormalFlag;

    private String abnormalReason;

    private String abnormalStatus;

    private Long operatorId;

    private String operatorName;

    private String remark;
}

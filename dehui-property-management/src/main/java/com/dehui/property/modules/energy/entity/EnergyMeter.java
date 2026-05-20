package com.dehui.property.modules.energy.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "energy_meter")
public class EnergyMeter extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String meterNo;

    @Column(nullable = false)
    private String meterType;

    private Long buildingId;

    private String buildingName;

    private Long floorId;

    private String floorName;

    private Long roomId;

    private String roomName;

    private Long tenantId;

    private String tenantName;

    private String installLocation;

    private String unit;

    private BigDecimal multiplier;

    private String billingMode;

    private String status;

    private String remark;
}

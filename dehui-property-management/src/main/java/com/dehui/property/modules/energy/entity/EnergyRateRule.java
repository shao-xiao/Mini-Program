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
@Table(name = "energy_rate_rule")
public class EnergyRateRule extends BaseEntity {
    @Column(nullable = false)
    private String energyType;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    private String status;

    private Boolean defaultRule;

    private String remark;
}

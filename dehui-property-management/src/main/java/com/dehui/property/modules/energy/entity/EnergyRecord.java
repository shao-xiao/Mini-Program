package com.dehui.property.modules.energy.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "energy_record")
public class EnergyRecord extends BaseEntity {
    @Column(nullable = false)
    private String meterNumber;
    
    private String energyType;
    
    private LocalDate recordDate;
    
    private BigDecimal reading;
    
    private BigDecimal consumption;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private Long billId;
    
    private Long buildingId;
    
    private Long roomId;
}

package com.dehui.property.modules.equipment.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "equipment")
public class Equipment extends BaseEntity {
    @Column(nullable = false)
    private String equipmentName;
    
    private String equipmentCode;
    
    private String equipmentType;
    
    private String location;
    
    @Column(nullable = false)
    private String status;
    
    private String manufacturer;
    
    private String model;

    private LocalDate installDate;

    private String remark;
}

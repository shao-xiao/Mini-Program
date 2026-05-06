package com.dehui.property.modules.building.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "building")
public class Building extends BaseEntity {
    @Column(nullable = false)
    private String buildingName;
    
    private String buildingCode;
    
    private String address;
    
    private Integer totalFloors;
    
    private String description;
    
    private String status;
}

package com.dehui.property.modules.tenant.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {
    @Column(nullable = false)
    private String tenantName;
    
    private String tenantCode;
    
    private String contactPerson;
    
    private String contactPhone;
    
    private String contactEmail;
    
    private String businessLicense;
    
    private String status;
}

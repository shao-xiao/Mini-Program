package com.dehui.property.modules.mobile.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "tenant_contact")
@EqualsAndHashCode(callSuper = true)
public class TenantContact extends BaseEntity {

    private Long tenantId;

    private String name;

    private String phone;

    private String role;

    private Boolean isPrimary;

    private String status;
}

package com.dehui.property.modules.system.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "sys_role")
@EqualsAndHashCode(callSuper = true)
public class SysRole extends BaseEntity {

    private String roleCode; // ADMIN / MANAGER / STAFF / SECURITY / CLEANER

    private String roleName;

    private String description;

    private String status; // ACTIVE / DISABLED
}
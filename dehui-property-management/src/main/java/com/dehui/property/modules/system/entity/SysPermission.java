package com.dehui.property.modules.system.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "sys_rbac_permission")
@EqualsAndHashCode(callSuper = true)
public class SysPermission extends BaseEntity {
    @Column(unique = true)
    private String permissionCode;

    private String permissionName;

    private String permissionType;

    private String module;

    private String description;
}

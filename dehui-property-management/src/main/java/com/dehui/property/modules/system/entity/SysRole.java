package com.dehui.property.modules.system.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "sys_role")
@EqualsAndHashCode(callSuper = true)
public class SysRole extends BaseEntity {

    @Column(unique = true)
    private String roleCode;

    private String roleName;

    private String description;

    private String status; // ENABLED / DISABLED,兼容旧 ACTIVE

    private java.time.LocalDateTime deletedAt;
}

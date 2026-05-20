package com.dehui.property.modules.system.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_sys_user")
@EqualsAndHashCode(callSuper = true)
public class SysUser extends BaseEntity {

    @Column(unique = true)
    private String username;

    private String password;

    private String passwordHash;

    private String realName;

    @Column(unique = true)
    private String phone;

    private String email;

    private String userType;

    private Long tenantId;

    private String department;

    private String status; // ENABLED / DISABLED,兼容旧 ACTIVE

    private LocalDateTime lastLoginAt;

    private LocalDateTime deletedAt;
}

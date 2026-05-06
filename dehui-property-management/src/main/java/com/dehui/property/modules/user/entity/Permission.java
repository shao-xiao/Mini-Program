package com.dehui.property.modules.user.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_permission")
public class Permission extends BaseEntity {
    @Column(nullable = false)
    private String permissionName;
    
    private String permissionKey;
    
    private String description;
}

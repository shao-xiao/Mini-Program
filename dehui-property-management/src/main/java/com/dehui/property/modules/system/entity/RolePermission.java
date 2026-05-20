package com.dehui.property.modules.system.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "role_permission", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"roleId", "permissionId"})
})
@EqualsAndHashCode(callSuper = true)
public class RolePermission extends BaseEntity {
    private Long roleId;

    private Long permissionId;
}

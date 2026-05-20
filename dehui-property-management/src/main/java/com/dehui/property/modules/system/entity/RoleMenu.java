package com.dehui.property.modules.system.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "role_menu", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"roleId", "menuId"})
})
@EqualsAndHashCode(callSuper = true)
public class RoleMenu extends BaseEntity {
    private Long roleId;

    private Long menuId;
}

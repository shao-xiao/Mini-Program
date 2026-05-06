package com.dehui.property.modules.system.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "user_role")
@EqualsAndHashCode(callSuper = true)
public class UserRole extends BaseEntity {

    private Long userId;

    private Long roleId;
}
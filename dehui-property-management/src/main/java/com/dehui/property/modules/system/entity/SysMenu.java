package com.dehui.property.modules.system.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "sys_menu")
@EqualsAndHashCode(callSuper = true)
public class SysMenu extends BaseEntity {
    private Long parentId;

    private String menuName;

    @Column(unique = true)
    private String menuCode;

    private String path;

    private String component;

    private String icon;

    private Integer sortOrder;

    private Boolean visible;

    private String status;
}

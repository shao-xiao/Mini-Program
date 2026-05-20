package com.dehui.property.modules.system.dto;

import lombok.Data;

@Data
public class PermissionResponse {
    private Long id;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private String module;
    private String description;
}

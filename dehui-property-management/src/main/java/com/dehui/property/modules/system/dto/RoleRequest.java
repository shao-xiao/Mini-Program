package com.dehui.property.modules.system.dto;

import lombok.Data;

@Data
public class RoleRequest {
    private String roleCode;
    private String roleName;
    private String description;
    private String status;
}

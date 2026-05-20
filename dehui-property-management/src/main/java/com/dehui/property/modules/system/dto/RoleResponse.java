package com.dehui.property.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleResponse {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private String status;
    private String statusText;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

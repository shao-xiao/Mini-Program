package com.dehui.property.modules.tenant.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantContactResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String name;
    private String phone;
    private String role;
    private Boolean isPrimary;
    private String status;
    private Boolean requirePasswordReset;
    private LocalDateTime lastBindTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String initialPassword;
}

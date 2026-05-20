package com.dehui.property.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserResponse {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String userType;
    private String userTypeText;
    private Long tenantId;
    private String tenantName;
    private String department;
    private String status;
    private String statusText;
    private List<Long> roleIds;
    private List<String> roleCodes;
    private List<String> roleNames;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

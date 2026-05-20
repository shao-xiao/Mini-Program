package com.dehui.property.modules.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserRequest {
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String userType;
    private Long tenantId;
    private String department;
    private String status;
    private List<Long> roleIds;
}

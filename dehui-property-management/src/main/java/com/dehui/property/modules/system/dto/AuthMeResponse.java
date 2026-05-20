package com.dehui.property.modules.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AuthMeResponse {
    private Long userId;
    private String username;
    private String realName;
    private String phone;
    private String userType;
    private Long tenantId;
    private String status;
    private List<String> roles;
    private List<String> permissions;
    private List<MenuResponse> menus;
}

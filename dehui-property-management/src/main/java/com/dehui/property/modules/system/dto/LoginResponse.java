package com.dehui.property.modules.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private Long userId;

    private String username;

    private List<String> roles;

    private List<String> permissions;

    private List<MenuResponse> menus;
}

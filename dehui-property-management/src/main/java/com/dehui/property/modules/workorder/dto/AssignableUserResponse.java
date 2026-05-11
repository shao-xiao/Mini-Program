package com.dehui.property.modules.workorder.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AssignableUserResponse {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private List<String> roleCodes;
    private List<String> roleNames;
}

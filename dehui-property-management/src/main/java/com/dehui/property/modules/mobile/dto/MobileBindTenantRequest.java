package com.dehui.property.modules.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MobileBindTenantRequest {
    private Long tenantId;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    private String password;

    private String name;

    private String role;

    private Boolean devMode;
}

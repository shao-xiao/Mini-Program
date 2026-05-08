package com.dehui.property.modules.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MobileBindTenantRequest {
    @NotNull(message = "租户不能为空")
    private Long tenantId;

    @NotBlank(message = "联系人姓名不能为空")
    private String name;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    private String role;
}

package com.dehui.property.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantContactRequest {
    @NotBlank(message = "联系人姓名不能为空")
    private String name;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    private String role;

    private Boolean isPrimary;

    private String status;
}

package com.dehui.property.modules.tenant.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TenantController {

    @GetMapping("/tenants")
    public ApiResponse<Void> tenants() {
        throw BusinessException.notImplemented("租户");
    }

    @GetMapping("/tenant-contacts")
    public ApiResponse<Void> tenantContacts() {
        throw BusinessException.notImplemented("租户联系人");
    }
}

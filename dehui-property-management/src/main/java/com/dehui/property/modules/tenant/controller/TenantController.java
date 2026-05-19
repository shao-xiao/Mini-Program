package com.dehui.property.modules.tenant.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.tenant.dto.TenantContactRequest;
import com.dehui.property.modules.tenant.dto.TenantContactResponse;
import com.dehui.property.modules.tenant.dto.TenantOverviewResponse;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.service.TenantContactService;
import com.dehui.property.modules.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;
    private final TenantContactService tenantContactService;

    @GetMapping("/list")
    public Result<List<Tenant>> list() {
        return Result.success(tenantService.findAll());
    }

    @PostMapping("/save")
    public Result<Tenant> save(@RequestBody Tenant tenant) {
        return Result.success(tenantService.save(tenant));
    }

    @GetMapping("/{tenantId}/overview")
    public Result<TenantOverviewResponse> overview(@PathVariable Long tenantId) {
        return tenantService.overview(tenantId);
    }

    @GetMapping("/{tenantId}/contacts")
    public Result<List<TenantContactResponse>> contacts(@PathVariable Long tenantId) {
        return tenantContactService.listByTenant(tenantId);
    }

    @PostMapping("/{tenantId}/contacts")
    public Result<TenantContactResponse> saveContact(
            @PathVariable Long tenantId,
            @Valid @RequestBody TenantContactRequest request) {
        return tenantContactService.save(tenantId, request);
    }

    @PostMapping("/contacts/{contactId}/deactivate")
    public Result<TenantContactResponse> deactivateContact(@PathVariable Long contactId) {
        return tenantContactService.deactivate(contactId);
    }

    @PostMapping("/contacts/{contactId}/reset-password")
    public Result<TenantContactResponse> resetContactPassword(@PathVariable Long contactId) {
        return tenantContactService.resetPassword(contactId);
    }
}

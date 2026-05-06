package com.dehui.property.modules.tenant.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;

    @GetMapping("/list")
    public Result<List<Tenant>> list() {
        return Result.success(tenantService.findAll());
    }

    @PostMapping("/save")
    public Result<Tenant> save(@RequestBody Tenant tenant) {
        return Result.success(tenantService.save(tenant));
    }
}

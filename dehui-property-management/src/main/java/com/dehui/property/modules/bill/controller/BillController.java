package com.dehui.property.modules.bill.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.dto.BillAuditRequest;
import com.dehui.property.modules.bill.dto.BillCreateRequest;
import com.dehui.property.modules.bill.dto.BillResponse;
import com.dehui.property.modules.bill.service.BillService;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.service.SystemUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bills")
@RequiredArgsConstructor
public class BillController {
    private final BillService billService;
    private final SystemUserService systemUserService;

    @PostMapping
    public Result<BillResponse> create(@Valid @RequestBody BillCreateRequest request) {
        return billService.create(request);
    }

    @GetMapping
    public Result<List<BillResponse>> list(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String billType) {
        return billService.findAll(tenantId, status, auditStatus, billType);
    }

    @GetMapping("/tenants/{tenantId}/bills")
    public Result<List<BillResponse>> getTenantBills(@PathVariable Long tenantId) {
        return billService.findByTenantId(tenantId);
    }

    @PostMapping("/{id}/pay")
    public Result<BillResponse> pay(@PathVariable Long id) {
        return billService.pay(id);
    }

    @PostMapping("/{id}/approve")
    public Result<BillResponse> approve(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) BillAuditRequest request) {
        return billService.approve(id, currentUsername(token), request);
    }

    @PostMapping("/{id}/reject")
    public Result<BillResponse> reject(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) BillAuditRequest request) {
        return billService.reject(id, currentUsername(token), request);
    }

    @GetMapping("/contracts/{contractId}")
    public Result<List<BillResponse>> getByContract(@PathVariable Long contractId) {
        return billService.findByContractId(contractId);
    }

    @GetMapping("/status/{status}")
    public Result<List<BillResponse>> getByStatus(@PathVariable String status) {
        return billService.findByStatus(status);
    }

    private String currentUsername(String token) {
        String normalized = normalizeToken(token);
        SysUser user = normalized == null ? null : systemUserService.getByToken(normalized);
        if (user == null) {
            return "system";
        }
        return user.getRealName() == null || user.getRealName().isBlank()
                ? user.getUsername()
                : user.getRealName();
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}

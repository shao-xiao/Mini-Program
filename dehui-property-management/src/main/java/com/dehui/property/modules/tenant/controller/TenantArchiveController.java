package com.dehui.property.modules.tenant.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.tenant.application.TenantArchiveService;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.BillItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.ContactItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.ContractItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.DirectoryItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.Overview;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.ServiceManagerItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.Spaces;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TenantArchiveController {

    private final TenantArchiveService tenantArchiveService;

    @GetMapping("/tenants/search")
    public ApiResponse<PageResponse<DirectoryItem>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(tenantArchiveService.directory(keyword, status, page, pageSize));
    }

    @GetMapping("/tenants/service-managers")
    public ApiResponse<List<ServiceManagerItem>> serviceManagers(
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(tenantArchiveService.serviceManagers(keyword));
    }

    @GetMapping("/tenants/{tenantId}/archive/overview")
    public ApiResponse<Overview> overview(@PathVariable Long tenantId) {
        return ApiResponse.success(tenantArchiveService.overview(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/archive/contacts")
    public ApiResponse<PageResponse<ContactItem>> contacts(
            @PathVariable Long tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(tenantArchiveService.contacts(tenantId, keyword, status, page, pageSize));
    }

    @GetMapping("/tenants/{tenantId}/archive/spaces")
    public ApiResponse<Spaces> spaces(
            @PathVariable Long tenantId,
            @RequestParam(required = false) Boolean current,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(tenantArchiveService.spaces(tenantId, current, contractId, page, pageSize));
    }

    @GetMapping("/tenants/{tenantId}/archive/contracts")
    public ApiResponse<PageResponse<ContractItem>> contracts(
            @PathVariable Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(tenantArchiveService.contracts(tenantId, status, page, pageSize));
    }

    @GetMapping("/tenants/{tenantId}/archive/bills")
    public ApiResponse<PageResponse<BillItem>> bills(
            @PathVariable Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String invoiceStatus,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(tenantArchiveService.bills(
                tenantId,
                status,
                invoiceStatus,
                contractId,
                page,
                pageSize
        ));
    }
}

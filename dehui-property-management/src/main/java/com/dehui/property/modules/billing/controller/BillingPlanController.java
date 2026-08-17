package com.dehui.property.modules.billing.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.billing.application.BillingPlanApplicationService;
import com.dehui.property.modules.billing.dto.BillingPlanActionRequest;
import com.dehui.property.modules.billing.dto.BillingPlanCreateRequest;
import com.dehui.property.modules.billing.dto.BillingPlanResponse;
import com.dehui.property.modules.billing.dto.BillingPlanSummaryResponse;
import com.dehui.property.modules.billing.dto.BillingPlanUpdateRequest;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/billing-plans")
@Deprecated(forRemoval = false)
public class BillingPlanController {

    private static final String SEARCH_API = "/billing-plans/search";
    private static final String SUMMARY_API = "/billing-plans/summary";

    private final BillingPlanApplicationService service;

    public BillingPlanController(BillingPlanApplicationService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<BillingPlanResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long leaseId,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(service.search(keyword, status, tenantId, contractId, leaseId,
                sortField, sortDirection, sorts, groupBy, customFilters, page, pageSize));
    }

    @GetMapping("/summary")
    public ApiResponse<BillingPlanSummaryResponse> summary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long leaseId,
            @RequestParam(required = false) String customFilters
    ) {
        return ApiResponse.success(service.summary(keyword, status, tenantId, contractId, leaseId, customFilters));
    }

    @GetMapping("/{id}")
    public ApiResponse<BillingPlanResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.find(id));
    }

    @PostMapping("")
    public ApiResponse<BillingPlanResponse> create(
            @Valid @RequestBody BillingPlanCreateRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.create(request, principal.userId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<BillingPlanResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody BillingPlanUpdateRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.update(id, request, principal.userId()));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<BillingPlanResponse> activate(
            @PathVariable Long id,
            @Valid @RequestBody BillingPlanActionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.activate(id, request, principal.userId()));
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<BillingPlanResponse> terminate(
            @PathVariable Long id,
            @Valid @RequestBody BillingPlanActionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.terminate(id, request, principal.userId()));
    }
}

package com.dehui.property.modules.lease.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.lease.application.LeaseApplicationService;
import com.dehui.property.modules.lease.dto.LeaseActionRequest;
import com.dehui.property.modules.lease.dto.LeaseCreateRequest;
import com.dehui.property.modules.lease.dto.LeaseRenewRequest;
import com.dehui.property.modules.lease.dto.LeaseResponse;
import com.dehui.property.modules.lease.dto.LeaseSummaryResponse;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/leases")
public class LeaseController {

    // Full route constants make the public contract easy to discover in generated documentation.
    private static final String SEARCH_API = "/leases/search";
    private static final String SUMMARY_API = "/leases/summary";

    private final LeaseApplicationService leaseService;

    public LeaseController(LeaseApplicationService leaseService) {
        this.leaseService = leaseService;
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<LeaseResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(leaseService.search(
                keyword, status, tenantId, contractId, sortField, sortDirection,
                sorts, groupBy, customFilters, page, pageSize
        ));
    }

    @GetMapping("/summary")
    public ApiResponse<LeaseSummaryResponse> summary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String customFilters
    ) {
        return ApiResponse.success(leaseService.summary(keyword, status, tenantId, contractId, customFilters));
    }

    @GetMapping("/{id}")
    public ApiResponse<LeaseResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(leaseService.find(id));
    }

    @PostMapping("")
    public ApiResponse<LeaseResponse> create(
            @Valid @RequestBody LeaseCreateRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(leaseService.create(request, principal.userId()));
    }

    @PostMapping("/{id}/check-in")
    public ApiResponse<LeaseResponse> checkIn(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LeaseActionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(leaseService.checkIn(id, request, principal.userId()));
    }

    @PostMapping("/{id}/check-out")
    public ApiResponse<LeaseResponse> checkOut(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LeaseActionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(leaseService.checkOut(id, request, principal.userId()));
    }

    @PostMapping("/{id}/renew")
    public ApiResponse<LeaseResponse> renew(
            @PathVariable Long id,
            @Valid @RequestBody LeaseRenewRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(leaseService.renew(id, request, principal.userId()));
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<LeaseResponse> terminate(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LeaseActionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(leaseService.terminate(id, request, principal.userId()));
    }
}

package com.dehui.property.modules.billing.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.billing.application.FeeRuleApplicationService;
import com.dehui.property.modules.billing.dto.FeeRuleActionRequest;
import com.dehui.property.modules.billing.dto.FeeRuleCreateRequest;
import com.dehui.property.modules.billing.dto.FeeRuleResponse;
import com.dehui.property.modules.billing.dto.FeeRuleSummaryResponse;
import com.dehui.property.modules.billing.dto.FeeRuleUpdateRequest;
import com.dehui.property.modules.billing.dto.FeeRuleVersionResponse;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/fee-rules")
@Deprecated(forRemoval = false)
public class FeeRuleController {

    private static final String SEARCH_API = "/fee-rules/search";
    private static final String SUMMARY_API = "/fee-rules/summary";

    private final FeeRuleApplicationService service;

    public FeeRuleController(FeeRuleApplicationService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<FeeRuleResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String calculationType,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(service.search(keyword, status, feeType, calculationType,
                sortField, sortDirection, sorts, groupBy, customFilters, page, pageSize));
    }

    @GetMapping("/summary")
    public ApiResponse<FeeRuleSummaryResponse> summary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String calculationType,
            @RequestParam(required = false) String customFilters
    ) {
        return ApiResponse.success(service.summary(keyword, status, feeType, calculationType, customFilters));
    }

    @GetMapping("/{id}")
    public ApiResponse<FeeRuleResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(service.find(id));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<FeeRuleVersionResponse>> versions(@PathVariable Long id) {
        return ApiResponse.success(service.versions(id));
    }

    @PostMapping("")
    public ApiResponse<FeeRuleResponse> create(
            @Valid @RequestBody FeeRuleCreateRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.create(request, principal.userId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<FeeRuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody FeeRuleUpdateRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.update(id, request, principal.userId()));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<FeeRuleResponse> activate(
            @PathVariable Long id,
            @Valid @RequestBody FeeRuleActionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.activate(id, request, principal.userId()));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<FeeRuleResponse> disable(
            @PathVariable Long id,
            @Valid @RequestBody FeeRuleActionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.disable(id, request, principal.userId()));
    }
}

package com.dehui.property.modules.legacy;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.billing.application.FeeRuleApplicationService;
import com.dehui.property.modules.billing.dto.FeeRuleCompatibilityRequest;
import com.dehui.property.modules.billing.dto.FeeRuleResponse;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyWriteController {

    private final FeeRuleApplicationService feeRuleService;

    public LegacyWriteController(FeeRuleApplicationService feeRuleService) {
        this.feeRuleService = feeRuleService;
    }

    @GetMapping("/assets")
    public ApiResponse<PageResponse<Object>> legacyLists(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        return ApiResponse.success(new PageResponse<>(List.of(), 0L, normalizedPage, normalizedPageSize));
    }

    @PostMapping("/assets")
    public ApiResponse<Void> legacyCreates() {
        throw BusinessException.notImplemented("legacy module");
    }

    /** @deprecated use /fee-rules/search. */
    @Deprecated
    @GetMapping("/feerules")
    public ApiResponse<PageResponse<FeeRuleResponse>> legacyFeeRules(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(feeRuleService.search(
                keyword, status, feeType, null, "updatedAt", "desc", page, pageSize
        ));
    }

    /** @deprecated use POST /fee-rules with FeeRuleCreateRequest. */
    @Deprecated
    @PostMapping("/feerules")
    public ApiResponse<FeeRuleResponse> legacyFeeRuleCreate(
            @Valid @RequestBody FeeRuleCompatibilityRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(feeRuleService.createCompatibility(request, principal.userId()));
    }

    @PutMapping({"/assets/{id}"})
    public ApiResponse<Void> legacyUpdates(@PathVariable Long id) {
        throw BusinessException.notImplemented("legacy module");
    }

    @DeleteMapping({"/assets/{id}"})
    public ApiResponse<Void> legacyDeletes(@PathVariable Long id) {
        throw BusinessException.notImplemented("legacy module");
    }

    @PostMapping({
            "/assets/{id}/transfer",
            "/feerules/{id}/generate-bill"
    })
    public ApiResponse<Void> legacyActions(@PathVariable Long id) {
        throw BusinessException.notImplemented("legacy module");
    }
}

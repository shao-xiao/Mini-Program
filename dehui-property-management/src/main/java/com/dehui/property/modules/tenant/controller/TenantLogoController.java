package com.dehui.property.modules.tenant.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.tenant.application.TenantLogoService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TenantLogoController {

    private final TenantLogoService tenantLogoService;

    @PutMapping("/tenants/{tenantId}/logo")
    public ApiResponse<Map<String, Object>> updateLogo(
            @PathVariable Long tenantId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        if (body == null || !body.containsKey("logoFileId")) {
            throw new BusinessException(400, "请明确提供 logoFileId，清除 Logo 时传 null");
        }
        return ApiResponse.success(tenantLogoService.updateLogo(
                tenantId,
                logoFileId(body.get("logoFileId")),
                principal.userId()
        ));
    }

    private Long logoFileId(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Long fileId = value instanceof Number number
                    ? number.longValue()
                    : Long.valueOf(String.valueOf(value).trim());
            if (fileId <= 0) {
                throw new NumberFormatException();
            }
            return fileId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "logoFileId 必须是有效的正整数");
        }
    }
}

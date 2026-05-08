package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileAuthResponse;
import com.dehui.property.modules.mobile.dto.MobileBindInternalRequest;
import com.dehui.property.modules.mobile.dto.MobileBindTenantRequest;
import com.dehui.property.modules.mobile.dto.MobileDevLoginRequest;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import com.dehui.property.modules.mobile.service.MobileAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mobile/auth")
@RequiredArgsConstructor
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    @PostMapping("/dev-login")
    public Result<MobileAuthResponse> devLogin(@RequestBody MobileDevLoginRequest request) {
        return Result.success(mobileAuthService.devLogin(request));
    }

    @GetMapping("/me")
    public Result<MobileUserProfile> me(@RequestHeader("Authorization") String token) {
        MobileUserProfile profile = mobileAuthService.getProfile(normalizeToken(token));
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        return Result.success(profile);
    }

    @PostMapping("/bind-internal")
    public Result<MobileAuthResponse> bindInternal(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody MobileBindInternalRequest request) {
        return mobileAuthService.bindInternal(normalizeToken(token), request);
    }

    @PostMapping("/bind-tenant")
    public Result<MobileAuthResponse> bindTenant(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody MobileBindTenantRequest request) {
        return mobileAuthService.bindTenant(normalizeToken(token), request);
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}

package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MobileProfileController {

    @GetMapping("/mobile/me")
    public ApiResponse<AuthPrincipal> me(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        return ApiResponse.success(principal);
    }

    @GetMapping("/mobile/mine/summary")
    public ApiResponse<Void> summary() {
        throw BusinessException.notImplemented("移动端我的摘要");
    }
}

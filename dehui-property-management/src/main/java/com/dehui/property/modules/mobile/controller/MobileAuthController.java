package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.mobile.dto.WechatLoginRequest;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import com.dehui.property.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mobile/auth")
@RequiredArgsConstructor
public class MobileAuthController {

    private final TokenService tokenService;

    @PostMapping("/wechat-login")
    public ApiResponse<Void> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        throw new BusinessException(501, "微信 code2Session 需要配置服务端 AppSecret 后接入");
    }

    @PostMapping("/bind-internal")
    public ApiResponse<Void> bindInternal() {
        throw BusinessException.notImplemented("移动端绑定内部员工");
    }

    @PostMapping("/bind-tenant")
    public ApiResponse<Void> bindTenant() {
        throw BusinessException.notImplemented("移动端绑定租户联系人");
    }

    @GetMapping("/me")
    public ApiResponse<AuthPrincipal> me(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        return ApiResponse.success(principal);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        tokenService.revoke(request.getHeader("Authorization"));
        return ApiResponse.success();
    }
}

package com.dehui.property.modules.system.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.system.dto.AuthMeResponse;
import com.dehui.property.modules.system.service.SystemUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final SystemUserService systemUserService;

    @GetMapping("/me")
    public Result<AuthMeResponse> me(@RequestHeader("Authorization") String token) {
        AuthMeResponse response = systemUserService.me(token);
        if (response == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        return Result.success(response);
    }
}

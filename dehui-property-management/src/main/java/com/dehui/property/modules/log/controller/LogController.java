package com.dehui.property.modules.log.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogController {

    @GetMapping("/logs/operations")
    public ApiResponse<Void> operationLogs() {
        throw BusinessException.notImplemented("操作日志");
    }

    @GetMapping("/logs/logins")
    public ApiResponse<Void> loginLogs() {
        throw BusinessException.notImplemented("登录日志");
    }
}

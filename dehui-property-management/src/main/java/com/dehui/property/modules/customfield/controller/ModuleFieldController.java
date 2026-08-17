package com.dehui.property.modules.customfield.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.customfield.application.ModuleFieldSchemaService;
import com.dehui.property.modules.customfield.application.ModuleFieldSettingService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
public class ModuleFieldController {

    private final ModuleFieldSchemaService service;
    private final ModuleFieldSettingService settings;

    public ModuleFieldController(ModuleFieldSchemaService service, ModuleFieldSettingService settings) {
        this.service = service;
        this.settings = settings;
    }

    @GetMapping("/module-fields/{moduleKey}")
    public ApiResponse<Map<String, Object>> schema(
            @PathVariable String moduleKey,
            @RequestParam(required = false) String surface,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.schema(moduleKey, surface, principal));
    }

    @GetMapping("/mobile/module-fields/{moduleKey}")
    public ApiResponse<Map<String, Object>> mobileSchema(
            @PathVariable String moduleKey,
            @RequestParam(defaultValue = "MINIPROGRAM_FORM") String surface,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.mobileSchema(moduleKey, surface, principal));
    }

    @PutMapping("/module-fields/{moduleKey}/{fieldKey}")
    public ApiResponse<Map<String, Object>> updateSystemField(
            @PathVariable String moduleKey,
            @PathVariable String fieldKey,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(settings.update(moduleKey, fieldKey, body, principal));
    }
}

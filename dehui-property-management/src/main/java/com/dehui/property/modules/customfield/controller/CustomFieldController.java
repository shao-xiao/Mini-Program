package com.dehui.property.modules.customfield.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.application.CustomFieldApplicationService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomFieldController {

    private final CustomFieldApplicationService service;

    public CustomFieldController(CustomFieldApplicationService service) {
        this.service = service;
    }

    @GetMapping({"/custom-fields/{workspaceKey}", "/workspaces/{workspaceKey}/custom-fields"})
    public ApiResponse<List<Map<String, Object>>> definitions(
            @PathVariable String workspaceKey,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.definitions(workspaceKey, includeDisabled, principal));
    }

    @PostMapping({"/custom-fields/{workspaceKey}", "/workspaces/{workspaceKey}/custom-fields"})
    public ApiResponse<Map<String, Object>> createDefinition(
            @PathVariable String workspaceKey,
            @RequestBody Map<String, Object> body,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.createDefinition(workspaceKey, body, principal, requestId));
    }

    @PutMapping("/workspaces/{workspaceKey}/custom-fields/{fieldId}")
    public ApiResponse<Map<String, Object>> updateDefinition(
            @PathVariable String workspaceKey,
            @PathVariable Long fieldId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.updateDefinition(workspaceKey, fieldId, body, principal, requestId));
    }

    @PostMapping("/workspaces/{workspaceKey}/custom-fields/{fieldId}/enable")
    public ApiResponse<Map<String, Object>> enableDefinition(
            @PathVariable String workspaceKey,
            @PathVariable Long fieldId,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.changeStatus(
                workspaceKey, fieldId, "ENABLED", false, principal, requestId
        ));
    }

    @PostMapping("/workspaces/{workspaceKey}/custom-fields/{fieldId}/disable")
    public ApiResponse<Map<String, Object>> disableDefinition(
            @PathVariable String workspaceKey,
            @PathVariable Long fieldId,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.changeStatus(
                workspaceKey, fieldId, "DISABLED", false, principal, requestId
        ));
    }

    @DeleteMapping("/workspaces/{workspaceKey}/custom-fields/{fieldId}")
    public ApiResponse<Map<String, Object>> deleteDefinition(
            @PathVariable String workspaceKey,
            @PathVariable Long fieldId,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.changeStatus(
                workspaceKey, fieldId, "DISABLED", true, principal, requestId
        ));
    }

    @PostMapping("/workspaces/{workspaceKey}/custom-fields/reorder")
    public ApiResponse<Void> reorderDefinitions(
            @PathVariable String workspaceKey,
            @RequestBody List<Map<String, Object>> order,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        service.reorder(workspaceKey, order, principal, requestId);
        return ApiResponse.success();
    }

    @GetMapping("/custom-fields/{workspaceKey}/values")
    public ApiResponse<Map<String, Map<String, Object>>> legacyValues(
            @PathVariable String workspaceKey,
            @RequestParam(required = false) String recordIds,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(stringKeyValues(service.values(workspaceKey, parseIds(recordIds), principal)));
    }

    @GetMapping("/workspaces/{workspaceKey}/records/{entityId}/custom-fields")
    public ApiResponse<Map<String, Object>> recordValues(
            @PathVariable String workspaceKey,
            @PathVariable Long entityId,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(service.values(workspaceKey, List.of(entityId), principal)
                .getOrDefault(entityId, Map.of()));
    }

    @PutMapping({
            "/custom-fields/{workspaceKey}/records/{entityId}",
            "/workspaces/{workspaceKey}/records/{entityId}/custom-fields"
    })
    public ApiResponse<Void> saveRecordValues(
            @PathVariable String workspaceKey,
            @PathVariable Long entityId,
            @RequestBody Map<String, Object> values,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        service.saveValues(workspaceKey, entityId, values, principal);
        return ApiResponse.success();
    }

    @PostMapping("/workspaces/{workspaceKey}/custom-field-values/batch-read")
    public ApiResponse<Map<String, Map<String, Object>>> batchRead(
            @PathVariable String workspaceKey,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(stringKeyValues(service.values(workspaceKey, idsFromBody(body), principal)));
    }

    @PutMapping("/workspaces/{workspaceKey}/custom-field-values/batch-update")
    public ApiResponse<List<Map<String, Object>>> batchUpdate(
            @PathVariable String workspaceKey,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        Object rawItems = body == null ? null : body.get("items");
        List<Map<String, Object>> items = new ArrayList<>();
        if (rawItems instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) throw new BusinessException(400, "批量更新项格式不合法");
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                items.add(normalized);
            }
        }
        return ApiResponse.success(service.batchUpdate(workspaceKey, items, principal));
    }

    private List<Long> parseIds(String recordIds) {
        if (recordIds == null || recordIds.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String part : recordIds.split(",")) {
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignored) {
                // Legacy endpoint keeps ignoring malformed IDs while still enforcing the batch limit.
            }
        }
        return ids.stream().distinct().limit(200).toList();
    }

    private List<Long> idsFromBody(Map<String, Object> body) {
        if (body == null) return List.of();
        Object rawIds = body.containsKey("entityIds") ? body.get("entityIds") : body.get("recordIds");
        if (!(rawIds instanceof List<?> list)) return List.of();
        List<Long> ids = new ArrayList<>();
        for (Object rawId : list) {
            try {
                long id = Long.parseLong(String.valueOf(rawId));
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignored) {
                throw new BusinessException(400, "业务记录 ID 不合法");
            }
        }
        if (ids.size() > 200) throw new BusinessException(400, "批量读取最多支持 200 条记录");
        return ids.stream().distinct().toList();
    }

    private Map<String, Map<String, Object>> stringKeyValues(Map<Long, Map<String, Object>> values) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        values.forEach((entityId, fields) -> result.put(String.valueOf(entityId), fields));
        return result;
    }
}

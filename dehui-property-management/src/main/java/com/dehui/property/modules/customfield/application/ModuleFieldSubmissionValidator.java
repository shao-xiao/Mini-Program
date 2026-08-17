package com.dehui.property.modules.customfield.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.security.AuthPrincipal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Validates canonical system-field submissions before a business controller
 * applies its state machine or derives relationship fields from the principal.
 */
@Service
public class ModuleFieldSubmissionValidator {

    private static final Set<String> SENSITIVE = Set.of(
            "tenantId", "tenantContactId", "mobileUserId", "sysUserId",
            "applicantMobileUserId", "submitterMobileUserId", "assigneeId",
            "inspectorId", "operatorId", "departmentId", "roleId", "permissionId"
    );
    private static final Set<String> ENVELOPE = Set.of("customFields");

    private final ModuleFieldSchemaService schemas;

    public ModuleFieldSubmissionValidator(ModuleFieldSchemaService schemas) {
        this.schemas = schemas;
    }

    public void validateMobileCreate(
            String moduleKey,
            Map<String, Object> body,
            AuthPrincipal principal,
            Set<String> compatibilityAliases
    ) {
        Map<String, Object> request = body == null ? Map.of() : body;
        for (String sensitive : SENSITIVE) {
            if (request.containsKey(sensitive)) {
                throw new BusinessException(400, "不得提交服务端派生关系字段: " + sensitive);
            }
        }
        Map<String, Object> schema = schemas.mobileSchema(moduleKey, "MINIPROGRAM_FORM", principal);
        List<Map<String, Object>> fields = fields(schema);
        Set<String> allowed = new LinkedHashSet<>(ENVELOPE);
        allowed.addAll(compatibilityAliases == null ? Set.of() : compatibilityAliases);
        fields.forEach(field -> {
            allowed.add(String.valueOf(field.get("fieldKey")));
            String apiField = String.valueOf(field.get("apiField"));
            if (!apiField.contains(".")) allowed.add(apiField);
        });
        request.keySet().forEach(key -> {
            if (!allowed.contains(key)) throw new BusinessException(400, "存在未注册的系统字段: " + key);
        });

        for (Map<String, Object> field : fields) {
            if (Boolean.TRUE.equals(field.get("readonly"))) continue;
            String apiField = String.valueOf(field.get("apiField"));
            if (apiField.startsWith("customFields.")) continue;
            Object value = first(request, apiField, String.valueOf(field.get("fieldKey")));
            if (Boolean.TRUE.equals(field.get("required")) && empty(value)) {
                throw new BusinessException(400, String.valueOf(field.get("label")) + "不能为空");
            }
            if (!empty(value)) validateType(field, value);
        }
    }

    private void validateType(Map<String, Object> field, Object value) {
        String type = String.valueOf(field.get("dataType")).toUpperCase();
        Map<String, Object> validation = map(field.get("validation"));
        if (Set.of("TEXT", "LONG_TEXT", "PHONE", "EMAIL", "URL").contains(type)) {
            int maxLength = integer(validation.get("maxLength"), "LONG_TEXT".equals(type) ? 10_000 : 200);
            if (String.valueOf(value).length() > maxLength) {
                throw new BusinessException(400, field.get("label") + "长度不能超过" + maxLength);
            }
            Object pattern = validation.get("pattern");
            if (pattern != null && !Pattern.compile(String.valueOf(pattern)).matcher(String.valueOf(value)).matches()) {
                throw new BusinessException(400, field.get("label") + "格式不正确");
            }
        }
        if (Set.of("NUMBER", "CURRENCY").contains(type)) {
            try {
                new java.math.BigDecimal(String.valueOf(value));
            } catch (NumberFormatException exception) {
                throw new BusinessException(400, field.get("label") + "必须是数字");
            }
        }
        if ("SELECT".equals(type) && field.get("options") instanceof Collection<?> options && !options.isEmpty()) {
            boolean known = options.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .anyMatch(option -> String.valueOf(option.get("code")).equals(String.valueOf(value)));
            if (!known) throw new BusinessException(400, field.get("label") + "选项无效");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fields(Map<String, Object> schema) {
        Object value = schema.get("fields");
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).<Map<String, Object>>map(item -> {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
            ((Map<?, ?>) item).forEach((key, entry) -> copy.put(String.valueOf(key), entry));
            return copy;
        }).toList();
    }

    private Object first(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.containsKey(key)) return body.get(key);
        }
        return null;
    }

    private boolean empty(Object value) {
        return value == null
                || value instanceof String text && text.isBlank()
                || value instanceof Collection<?> collection && collection.isEmpty();
    }

    private int integer(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}

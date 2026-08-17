package com.dehui.property.modules.customfield.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.security.AuthPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists configuration overlays for SYSTEM fields registered by
 * {@link ModuleFieldRegistry}. The registry remains the canonical source for
 * field keys, types, API mappings and storage mappings.
 */
@Service
public class ModuleFieldSettingService {

    private static final Set<String> SURFACES = Set.of(
            "adminList", "adminForm", "adminDetail", "miniProgramForm", "miniProgramDetail"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ModuleFieldRegistry registry;
    private final CustomFieldWorkspaceRegistry workspaces;
    private final CustomFieldAuthorizationService authorization;

    public ModuleFieldSettingService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ModuleFieldRegistry registry,
            CustomFieldWorkspaceRegistry workspaces,
            CustomFieldAuthorizationService authorization
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.workspaces = workspaces;
        this.authorization = authorization;
    }

    public List<Map<String, Object>> apply(String rawModuleKey, List<Map<String, Object>> sourceFields) {
        String moduleKey = registry.canonicalModuleKey(rawModuleKey);
        Map<String, Map<String, Object>> settings = new LinkedHashMap<>();
        jdbcTemplate.queryForList(
                """
                SELECT field_key, label_override, description, required_override,
                       validation_json, surfaces_json, version
                FROM module_field_setting
                WHERE module_key = ? AND status = 'ENABLED' AND deleted = 0
                """,
                moduleKey
        ).forEach(row -> settings.put(String.valueOf(row.get("field_key")), row));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : sourceFields) {
            LinkedHashMap<String, Object> field = new LinkedHashMap<>(source);
            Map<String, Object> setting = settings.get(String.valueOf(source.get("fieldKey")));
            if (setting != null) {
                String label = text(setting.get("label_override"));
                if (label != null) field.put("label", label);
                field.put("description", valueOrEmpty(setting.get("description")));
                if (setting.get("required_override") != null) {
                    boolean configured = booleanValue(setting.get("required_override"));
                    field.put("required", Boolean.TRUE.equals(source.get("required")) || configured);
                }
                field.put("validation", mergeMaps(map(source.get("validation")), readMap(setting.get("validation_json"))));
                field.put("surfaces", mergeSurfaces(map(source.get("surfaces")), readMap(setting.get("surfaces_json")),
                        Boolean.TRUE.equals(source.get("readonly"))));
                field.put("settingVersion", longValue(setting.get("version")));
            } else {
                field.putIfAbsent("description", "");
                field.put("settingVersion", 0L);
            }
            result.add(field);
        }
        return List.copyOf(result);
    }

    @Transactional
    public Map<String, Object> update(
            String rawModuleKey,
            String fieldKey,
            Map<String, Object> body,
            AuthPrincipal principal
    ) {
        String moduleKey = registry.canonicalModuleKey(rawModuleKey);
        var workspace = workspaces.require(moduleKey);
        authorization.requireManage(principal, workspace);
        Map<String, Object> systemField = registry.systemFields(moduleKey).stream()
                .filter(field -> fieldKey.equals(String.valueOf(field.get("fieldKey"))))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "系统字段不存在"));

        Map<String, Object> request = body == null ? Map.of() : body;
        if (request.containsKey("fieldKey") && !fieldKey.equals(String.valueOf(request.get("fieldKey")))) {
            throw new BusinessException(400, "系统字段编码不可修改");
        }
        if (request.containsKey("dataType")
                && !String.valueOf(systemField.get("dataType")).equalsIgnoreCase(String.valueOf(request.get("dataType")))) {
            throw new BusinessException(400, "系统字段类型不可修改");
        }

        String label = text(request.get("label"));
        if (label != null && label.length() > 100) throw new BusinessException(400, "字段名称最多100个字符");
        String description = text(request.get("description"));
        if (description != null && description.length() > 500) throw new BusinessException(400, "字段说明最多500个字符");
        Boolean required = request.containsKey("required") ? booleanValue(request.get("required")) : null;
        Map<String, Object> validation = normalizeValidation(map(request.get("validation")));
        Map<String, Object> surfaces = normalizeSurfaces(map(request.get("surfaces")), Boolean.TRUE.equals(systemField.get("readonly")));
        Long actorId = principal == null || principal.userId() == null ? 0L : principal.userId();

        jdbcTemplate.update(
                """
                INSERT INTO module_field_setting (
                    module_key, field_key, label_override, description, required_override,
                    validation_json, surfaces_json, version, status,
                    created_by, updated_by, deleted
                )
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), 0, 'ENABLED', ?, ?, 0)
                ON DUPLICATE KEY UPDATE
                    label_override = VALUES(label_override),
                    description = VALUES(description),
                    required_override = VALUES(required_override),
                    validation_json = VALUES(validation_json),
                    surfaces_json = VALUES(surfaces_json),
                    version = version + 1,
                    status = 'ENABLED',
                    updated_by = VALUES(updated_by),
                    deleted = 0
                """,
                moduleKey,
                fieldKey,
                label,
                description,
                required,
                writeJson(validation),
                writeJson(surfaces),
                actorId,
                actorId
        );
        return apply(moduleKey, registry.systemFields(moduleKey)).stream()
                .filter(field -> fieldKey.equals(field.get("fieldKey")))
                .findFirst()
                .orElseThrow(() -> new BusinessException(500, "字段配置保存后读取失败"));
    }

    private Map<String, Object> normalizeValidation(Map<String, Object> input) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("maxLength", "min", "max", "maxFiles", "pattern")) {
            if (input.containsKey(key)) result.put(key, input.get(key));
        }
        if (result.get("maxFiles") != null) {
            int maxFiles = Integer.parseInt(String.valueOf(result.get("maxFiles")));
            if (maxFiles < 1 || maxFiles > 30) throw new BusinessException(400, "文件数量限制必须在1到30之间");
            result.put("maxFiles", maxFiles);
        }
        return result;
    }

    private Map<String, Object> normalizeSurfaces(Map<String, Object> input, boolean systemReadonly) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String surface : SURFACES) {
            Map<String, Object> source = map(input.get(surface));
            boolean form = surface.endsWith("Form");
            boolean visible = !source.containsKey("visible") || booleanValue(source.get("visible"));
            boolean editable = form && !systemReadonly
                    && (!source.containsKey("editable") || booleanValue(source.get("editable")));
            int order = source.containsKey("order") ? Integer.parseInt(String.valueOf(source.get("order"))) : 0;
            result.put(surface, Map.of("visible", visible, "editable", editable, "order", order));
        }
        return result;
    }

    private Map<String, Object> mergeSurfaces(
            Map<String, Object> defaults,
            Map<String, Object> configured,
            boolean systemReadonly
    ) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String surface : SURFACES) {
            Map<String, Object> base = map(defaults.get(surface));
            Map<String, Object> override = map(configured.get(surface));
            boolean visible = override.containsKey("visible")
                    ? booleanValue(override.get("visible"))
                    : booleanValue(base.get("visible"));
            boolean editable = override.containsKey("editable")
                    ? booleanValue(override.get("editable"))
                    : booleanValue(base.get("editable"));
            int order = override.containsKey("order")
                    ? Integer.parseInt(String.valueOf(override.get("order")))
                    : Integer.parseInt(String.valueOf(base.getOrDefault("order", 0)));
            result.put(surface, Map.of(
                    "visible", visible,
                    "editable", !systemReadonly && surface.endsWith("Form") && editable,
                    "order", order
            ));
        }
        return result;
    }

    private Map<String, Object> mergeMaps(Map<String, Object> left, Map<String, Object> right) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(left);
        result.putAll(right);
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new BusinessException(400, "字段配置JSON无效");
        }
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return Map.of();
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Object valueOrEmpty(Object value) {
        return value == null ? "" : value;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }
}

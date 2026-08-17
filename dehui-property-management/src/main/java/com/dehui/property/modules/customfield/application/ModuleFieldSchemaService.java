package com.dehui.property.modules.customfield.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ModuleFieldSchemaService {

    private static final Set<String> MOBILE_SENSITIVE_FIELDS = Set.of(
            "tenantid",
            "tenantcontactid",
            "sysuserid",
            "mobileuserid",
            "applicantmobileuserid",
            "submittermobileuserid",
            "assigneeid",
            "handlerid",
            "processorid",
            "inspectorid",
            "operatorid",
            "responsibleuserid",
            "createdby",
            "updatedby"
    );

    private final ModuleFieldRegistry registry;
    private final CustomFieldApplicationService customFields;
    private final ModuleFieldSettingService settings;

    public ModuleFieldSchemaService(
            ModuleFieldRegistry registry,
            CustomFieldApplicationService customFields
    ) {
        this(registry, customFields, null);
    }

    @Autowired
    public ModuleFieldSchemaService(
            ModuleFieldRegistry registry,
            CustomFieldApplicationService customFields,
            ModuleFieldSettingService settings
    ) {
        this.registry = registry;
        this.customFields = customFields;
        this.settings = settings;
    }

    public Map<String, Object> schema(String rawModuleKey, String rawSurface, AuthPrincipal principal) {
        String moduleKey = registry.canonicalModuleKey(rawModuleKey);
        String surface = surfaceKey(rawSurface);
        List<Map<String, Object>> fields = new ArrayList<>();
        List<Map<String, Object>> systemFields = registry.systemFields(moduleKey);
        if (settings != null) {
            systemFields = settings.apply(moduleKey, systemFields);
        }
        systemFields.forEach(field -> fields.add(copy(field)));
        customFields.definitions(moduleKey, false, principal).forEach(definition ->
                fields.add(customField(definition))
        );

        List<Map<String, Object>> filtered = fields.stream()
                .filter(field -> surface == null || surfaceVisible(field, surface))
                .sorted(Comparator.comparingInt(field -> surfaceOrder(field, surface)))
                .map(field -> surface == null ? field : forSurface(field, surface))
                .toList();

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("version", 1);
        response.put("moduleKey", moduleKey);
        response.put("surface", surface == null ? "" : surface);
        response.put("fields", filtered);
        return response;
    }

    public Map<String, Object> mobileSchema(
            String moduleKey,
            String rawSurface,
            AuthPrincipal principal
    ) {
        String normalized = surfaceKey(rawSurface);
        if (!"miniProgramForm".equals(normalized) && !"miniProgramDetail".equals(normalized)) {
            throw new BusinessException(400, "小程序字段接口仅支持 MINIPROGRAM_FORM 或 MINIPROGRAM_DETAIL");
        }
        Map<String, Object> schema = new LinkedHashMap<>(schema(moduleKey, normalized, principal));
        Object rawFields = schema.get("fields");
        if (rawFields instanceof List<?> fields) {
            schema.put("fields", fields.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .filter(field -> !mobileSensitive(field))
                    .toList());
        }
        return schema;
    }

    private boolean mobileSensitive(Map<?, ?> field) {
        return List.of("fieldKey", "apiField", "storageField").stream()
                .map(field::get)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .map(value -> value.replaceAll("[^A-Za-z0-9]", "").toLowerCase())
                .anyMatch(MOBILE_SENSITIVE_FIELDS::contains);
    }

    private Map<String, Object> customField(Map<String, Object> definition) {
        String fieldKey = String.valueOf(definition.get("fieldCode"));
        String dataType = String.valueOf(definition.get("fieldType"));
        Map<String, Object> config = map(definition.get("config"));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("fieldKey", fieldKey);
        result.put("label", definition.get("fieldName"));
        result.put("source", "CUSTOM");
        result.put("dataType", dataType);
        result.put("apiField", "customFields." + fieldKey);
        result.put("storageField", "custom_field_value");
        result.put("required", Boolean.TRUE.equals(definition.get("required")));
        result.put("readonly", false);
        result.put("sortable", Boolean.TRUE.equals(definition.get("sortable")));
        result.put("filterable", true);
        result.put("options", normalizeOptions(definition.get("optionItems")));
        LinkedHashMap<String, Object> validation = new LinkedHashMap<>(config);
        validation.remove("surfaces");
        result.put("validation", validation);
        result.put("surfaces", normalizeSurfaces(config.get("surfaces"), number(definition.get("displayOrder"))));
        result.put("definitionId", definition.get("id"));
        result.put("status", definition.get("status"));
        return result;
    }

    private List<Map<String, String>> normalizeOptions(Object rawOptions) {
        if (!(rawOptions instanceof List<?> options)) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (Object rawOption : options) {
            Map<String, Object> option = map(rawOption);
            if (!"DISABLED".equals(option.get("status"))) {
                result.add(Map.of(
                        "code", String.valueOf(option.get("optionCode")),
                        "label", String.valueOf(option.get("optionLabel"))
                ));
            }
        }
        return result;
    }

    private Map<String, Object> normalizeSurfaces(Object rawSurfaces, int order) {
        Map<String, Object> configured = map(rawSurfaces);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("adminList", "adminForm", "adminDetail", "miniProgramForm", "miniProgramDetail")) {
            Map<String, Object> source = map(configured.get(key));
            boolean visible = source.containsKey("visible") ? Boolean.TRUE.equals(source.get("visible")) : true;
            boolean editable = key.endsWith("Form")
                    && (!source.containsKey("editable") || Boolean.TRUE.equals(source.get("editable")));
            int surfaceOrder = source.containsKey("order") ? number(source.get("order")) : order;
            result.put(key, Map.of("visible", visible, "editable", editable, "order", surfaceOrder));
        }
        return result;
    }

    private boolean surfaceVisible(Map<String, Object> field, String surface) {
        return Boolean.TRUE.equals(map(map(field.get("surfaces")).get(surface)).get("visible"));
    }

    private Map<String, Object> forSurface(Map<String, Object> field, String surface) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(field);
        Map<String, Object> settings = map(map(field.get("surfaces")).get(surface));
        boolean editable = Boolean.TRUE.equals(settings.get("editable"));
        result.put("visible", Boolean.TRUE.equals(settings.get("visible")));
        result.put("order", number(settings.get("order")));
        result.put("readonly", Boolean.TRUE.equals(field.get("readonly")) || !editable);
        return result;
    }

    private int surfaceOrder(Map<String, Object> field, String surface) {
        if (surface == null) {
            return number(map(map(field.get("surfaces")).get("adminList")).get("order"));
        }
        return number(map(map(field.get("surfaces")).get(surface)).get("order"));
    }

    private String surfaceKey(String rawSurface) {
        if (rawSurface == null || rawSurface.isBlank()) return null;
        String compact = rawSurface.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return switch (compact) {
            case "ADMINLIST" -> "adminList";
            case "ADMINFORM" -> "adminForm";
            case "ADMINDETAIL" -> "adminDetail";
            case "MINIPROGRAMFORM" -> "miniProgramForm";
            case "MINIPROGRAMDETAIL" -> "miniProgramDetail";
            default -> throw new IllegalArgumentException("Unsupported module-field surface: " + rawSurface);
        };
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}

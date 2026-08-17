package com.dehui.property.modules.customfield.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.customfield.domain.CustomFieldValueValidator;
import com.dehui.property.modules.customfield.domain.ValidatedCustomFieldValue;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.DefinitionRecord;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.DefinitionWrite;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.FileMetadata;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.OptionRecord;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.OptionWrite;
import com.dehui.property.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomFieldApplicationService {

    private static final Pattern FIELD_CODE = Pattern.compile("^custom_[A-Za-z0-9_]{3,80}$");
    private static final Pattern OPTION_COLOR = Pattern.compile("^(#[0-9A-Fa-f]{6}|[a-z][a-z0-9-]{1,30})$");
    private static final int MAX_OPTIONS = 100;

    private final CustomFieldWorkspaceRegistry registry;
    private final CustomFieldAuthorizationService authorization;
    private final CustomFieldRepository repository;
    private final CustomFieldValueValidator valueValidator;
    private final ObjectMapper objectMapper;

    public CustomFieldApplicationService(
            CustomFieldWorkspaceRegistry registry,
            CustomFieldAuthorizationService authorization,
            CustomFieldRepository repository,
            ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.authorization = authorization;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.valueValidator = new CustomFieldValueValidator();
    }

    public List<Map<String, Object>> definitions(String workspaceKey, boolean includeDisabled, AuthPrincipal principal) {
        var workspace = registry.require(workspaceKey);
        authorization.requireView(principal, workspace);
        return repository.findDefinitions(workspaceKey, !includeDisabled).stream()
                .map(this::definitionResponse)
                .toList();
    }

    @Transactional
    public Map<String, Object> createDefinition(
            String workspaceKey,
            Map<String, Object> request,
            AuthPrincipal principal,
            String requestId
    ) {
        var workspace = registry.require(workspaceKey);
        authorization.requireManage(principal, workspace);
        if (repository.countActiveDefinitions(workspaceKey) >= workspace.maximumCustomFields()) {
            throw new BusinessException(409, "当前工作区自定义字段数量已达上限");
        }

        DefinitionWrite write = definitionWrite(workspace, request, null);
        ensureUniqueName(workspaceKey, write.fieldName(), null);
        Long fieldId = repository.insertDefinition(write, operatorId(principal));
        List<OptionWrite> options = normalizeOptions(write.fieldType(), optionsInput(request, null));
        repository.replaceOptions(fieldId, options, operatorId(principal));
        DefinitionRecord created = repository.findDefinition(workspaceKey, fieldId)
                .orElseThrow(() -> new BusinessException(500, "自定义字段创建后读取失败"));
        validateDefaultValue(created);
        if (created.required() && repository.hasMissingValues(workspace, fieldId)) {
            throw new BusinessException(409, "现有记录尚未填写该字段，不能直接创建为必填字段");
        }
        repository.addHistory(fieldId, "CREATE", null, definitionResponse(created), operatorId(principal), requestId(requestId));
        return definitionResponse(created);
    }

    @Transactional
    public Map<String, Object> updateDefinition(
            String workspaceKey,
            Long fieldId,
            Map<String, Object> request,
            AuthPrincipal principal,
            String requestId
    ) {
        var workspace = registry.require(workspaceKey);
        authorization.requireManage(principal, workspace);
        DefinitionRecord current = requireDefinition(workspaceKey, fieldId);
        DefinitionWrite write = definitionWrite(workspace, request, current);
        ensureUniqueName(workspaceKey, write.fieldName(), fieldId);
        if (write.fieldType() != current.fieldType() && repository.hasValues(fieldId)) {
            throw new BusinessException(409, "字段已有数据，不能修改字段类型");
        }
        if (!current.required() && write.required() && repository.hasMissingValues(workspace, fieldId)) {
            throw new BusinessException(409, "现有记录存在空值，不能直接改为必填字段");
        }
        long expectedVersion = longValue(request, "version", current.version());
        repository.updateDefinition(fieldId, expectedVersion, write, operatorId(principal));
        List<OptionWrite> options = normalizeOptions(write.fieldType(), optionsInput(request, current));
        repository.replaceOptions(fieldId, options, operatorId(principal));
        DefinitionRecord updated = requireDefinition(workspaceKey, fieldId);
        validateDefaultValue(updated);
        repository.addHistory(fieldId, "UPDATE", definitionResponse(current), definitionResponse(updated),
                operatorId(principal), requestId(requestId));
        return definitionResponse(updated);
    }

    @Transactional
    public Map<String, Object> changeStatus(
            String workspaceKey,
            Long fieldId,
            String targetStatus,
            boolean deleted,
            AuthPrincipal principal,
            String requestId
    ) {
        var workspace = registry.require(workspaceKey);
        authorization.requireManage(principal, workspace);
        DefinitionRecord current = requireDefinition(workspaceKey, fieldId);
        repository.updateStatus(workspaceKey, fieldId, targetStatus, deleted, operatorId(principal));
        Map<String, Object> after = deleted ? Map.of("deleted", true) : definitionResponse(requireDefinition(workspaceKey, fieldId));
        repository.addHistory(fieldId, deleted ? "DELETE" : targetStatus, definitionResponse(current), after,
                operatorId(principal), requestId(requestId));
        return after;
    }

    @Transactional
    public void reorder(
            String workspaceKey,
            List<Map<String, Object>> order,
            AuthPrincipal principal,
            String requestId
    ) {
        var workspace = registry.require(workspaceKey);
        authorization.requireManage(principal, workspace);
        if (order == null) return;
        Set<Long> seen = new LinkedHashSet<>();
        for (int index = 0; index < order.size(); index++) {
            Map<String, Object> item = order.get(index);
            Long fieldId = requiredLong(item, "fieldId", "id");
            if (!seen.add(fieldId)) throw new BusinessException(400, "字段排序包含重复项");
            requireDefinition(workspaceKey, fieldId);
            int displayOrder = intValue(item, "displayOrder", index);
            repository.updateOrder(workspaceKey, fieldId, displayOrder, operatorId(principal));
            repository.addHistory(fieldId, "REORDER", null, Map.of("displayOrder", displayOrder),
                    operatorId(principal), requestId(requestId));
        }
    }

    public Map<Long, Map<String, Object>> values(
            String workspaceKey,
            List<Long> entityIds,
            AuthPrincipal principal
    ) {
        var workspace = registry.require(workspaceKey);
        authorization.requireView(principal, workspace);
        List<Long> safeIds = entityIds == null ? List.of() : entityIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(200)
                .toList();
        return repository.findValues(workspaceKey, safeIds);
    }

    @Transactional
    public void saveValues(
            String workspaceKey,
            Long entityId,
            Map<String, Object> values,
            AuthPrincipal principal
    ) {
        var workspace = registry.require(workspaceKey);
        authorization.requireValueUpdate(principal, workspace);
        if (entityId == null || entityId <= 0 || !repository.entityExists(workspace, entityId)) {
            throw new BusinessException(404, "业务记录不存在或无权访问");
        }
        Map<String, Object> submitted = values == null ? Map.of() : values;
        List<DefinitionRecord> definitions = repository.findDefinitions(workspaceKey, true);
        Set<String> definedCodes = definitions.stream().map(DefinitionRecord::fieldCode).collect(java.util.stream.Collectors.toSet());
        submitted.keySet().forEach(fieldCode -> {
            if (!definedCodes.contains(fieldCode)) throw new BusinessException(400, "存在未注册的自定义字段");
        });

        Map<DefinitionRecord, ValidatedCustomFieldValue> validated = new LinkedHashMap<>();
        for (DefinitionRecord definition : definitions) {
            ValidatedCustomFieldValue value = valueValidator.validate(
                    definition.snapshot(),
                    submitted.get(definition.fieldCode())
            );
            if (definition.uniqueValue() && !value.equals(ValidatedCustomFieldValue.empty())
                    && repository.uniqueValueExists(definition, entityId, value)) {
                throw new BusinessException(409, definition.fieldName() + "的值已存在");
            }
            validated.put(definition, value);
        }
        validateFileReferences(validated);
        Set<Long> fileIds = new LinkedHashSet<>();
        validated.forEach((definition, value) -> {
            if (definition.fieldType() == CustomFieldType.IMAGE || definition.fieldType() == CustomFieldType.ATTACHMENT) {
                fileIds.addAll(fileIds(value.jsonValue()));
            }
        });
        repository.claimInternalFiles(workspace, entityId, fileIds, operatorId(principal));
        repository.replaceValues(workspace, entityId, validated, operatorId(principal));
    }

    @Transactional
    public List<Map<String, Object>> batchUpdate(
            String workspaceKey,
            List<Map<String, Object>> items,
            AuthPrincipal principal
    ) {
        var workspace = registry.require(workspaceKey);
        authorization.requireValueUpdate(principal, workspace);
        if (items == null || items.isEmpty()) return List.of();
        if (items.size() > 100) throw new BusinessException(400, "批量更新最多支持 100 条记录");
        List<Long> orderedEntityIds = items.stream()
                .map(item -> requiredLong(item, "entityId", "recordId"))
                .distinct()
                .sorted()
                .toList();
        repository.lockEntitiesForUpdate(workspace, orderedEntityIds);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Long entityId = requiredLong(item, "entityId", "recordId");
            Object rawValues = item.get("values");
            Map<String, Object> values = rawValues instanceof Map<?, ?> map ? stringKeyMap(map) : Map.of();
            try {
                saveValues(workspaceKey, entityId, values, principal);
                results.add(Map.of("entityId", entityId, "success", true));
            } catch (BusinessException exception) {
                results.add(Map.of("entityId", entityId, "success", false, "message", exception.getMessage()));
            }
        }
        return results;
    }

    private DefinitionWrite definitionWrite(
            CustomFieldWorkspaceRegistry.Registration workspace,
            Map<String, Object> request,
            DefinitionRecord current
    ) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String fieldCode = current == null
                ? fieldCode(stringValue(body, "fieldCode", "fieldKey", "key"))
                : current.fieldCode();
        String fieldName = requiredText(body, current == null ? null : current.fieldName(), "fieldName", "label", "name");
        String rawType = stringValue(body, "fieldType", "type");
        CustomFieldType fieldType = rawType == null && current != null
                ? current.fieldType()
                : parseImplementedType(rawType == null ? "TEXT" : rawType);
        String description = text(body, current == null ? null : current.description(), 500, "description");
        boolean required = boolValue(body, "required", current != null && current.required());
        boolean uniqueValue = boolValue(body, "uniqueValue", current != null && current.uniqueValue());
        if (uniqueValue && !supportsUniqueValue(fieldType)) {
            throw new BusinessException(400, "当前字段类型不支持唯一值约束");
        }
        Map<String, Object> config = body.get("config") instanceof Map<?, ?> map
                ? stringKeyMap(map)
                : current == null ? Map.of() : current.config();
        List<OptionWrite> options = normalizeOptions(fieldType, optionsInput(body, current));
        boolean sortable = boolValue(body, "sortable", current == null || current.sortable());
        boolean requestedGroupable = boolValue(body, "groupable", current != null && current.groupable());
        boolean groupable = requestedGroupable && supportsGrouping(fieldType);
        int displayOrder = intValue(body, "displayOrder", intValue(body, "sortOrder", current == null ? 0 : current.displayOrder()));
        Object defaultValue = body.containsKey("defaultValue") ? body.get("defaultValue") : current == null ? null : current.defaultValue();

        return new DefinitionWrite(
                workspace.workspaceKey(), workspace.entityType(), fieldCode, fieldName, fieldType, description,
                required, uniqueValue, defaultValue == null ? null : String.valueOf(defaultValue),
                writeJson(config), writeJson(options.stream().map(OptionWrite::optionLabel).toList()),
                displayOrder, sortable, groupable
        );
    }

    private List<OptionWrite> normalizeOptions(CustomFieldType fieldType, Object rawOptions) {
        if (fieldType != CustomFieldType.SINGLE_SELECT && fieldType != CustomFieldType.MULTI_SELECT) return List.of();
        if (!(rawOptions instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(400, "单选和多选字段至少需要一个选项");
        }
        if (list.size() > MAX_OPTIONS) throw new BusinessException(400, "字段选项数量超过限制");
        List<OptionWrite> result = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<String> labels = new LinkedHashSet<>();
        for (int index = 0; index < list.size(); index++) {
            Object item = list.get(index);
            String optionCode;
            String optionLabel;
            String color = null;
            int displayOrder = index;
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = stringKeyMap(rawMap);
                optionLabel = requiredText(map, null, "optionLabel", "label", "name");
                optionCode = stringValue(map, "optionCode", "code", "value");
                if (optionCode == null || optionCode.isBlank()) optionCode = "opt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                color = text(map, null, 32, "color");
                displayOrder = intValue(map, "displayOrder", index);
            } else {
                optionLabel = cleanLabel(String.valueOf(item), 100, "选项名称");
                optionCode = optionLabel;
            }
            if (optionCode.length() > 64 || !codes.add(optionCode)) throw new BusinessException(400, "选项编码重复或过长");
            if (!labels.add(optionLabel.toLowerCase(Locale.ROOT))) throw new BusinessException(400, "选项名称重复");
            if (color != null && !color.isBlank() && !OPTION_COLOR.matcher(color).matches()) {
                throw new BusinessException(400, "选项颜色格式不合法");
            }
            result.add(new OptionWrite(optionCode, optionLabel, color, displayOrder));
        }
        return result;
    }

    private Map<String, Object> definitionResponse(DefinitionRecord definition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", definition.id());
        result.put("key", definition.fieldCode());
        result.put("fieldCode", definition.fieldCode());
        result.put("label", definition.fieldName());
        result.put("fieldName", definition.fieldName());
        result.put("type", legacyWireType(definition.fieldType()));
        result.put("fieldType", definition.fieldType().name());
        result.put("description", definition.description());
        result.put("required", definition.required());
        result.put("uniqueValue", definition.uniqueValue());
        result.put("defaultValue", definition.defaultValue() == null ? "" : definition.defaultValue());
        result.put("config", definition.config());
        result.put("sortOrder", definition.displayOrder());
        result.put("displayOrder", definition.displayOrder());
        result.put("custom", true);
        result.put("sortable", definition.sortable());
        result.put("groupable", definition.groupable());
        result.put("status", definition.status());
        result.put("version", definition.version());
        result.put("options", definition.options().stream().map(OptionRecord::optionLabel).toList());
        result.put("optionItems", definition.options().stream().map(option -> Map.of(
                "optionCode", option.optionCode(),
                "optionLabel", option.optionLabel(),
                "color", option.color() == null ? "" : option.color(),
                "displayOrder", option.displayOrder(),
                "status", option.status()
        )).toList());
        return result;
    }

    private void ensureUniqueName(String workspaceKey, String fieldName, Long excludedId) {
        boolean duplicate = repository.findDefinitions(workspaceKey, false).stream()
                .anyMatch(field -> !field.id().equals(excludedId) && field.fieldName().equalsIgnoreCase(fieldName));
        if (duplicate) throw new BusinessException(409, "当前工作区已存在同名字段");
    }

    private void validateDefaultValue(DefinitionRecord definition) {
        if (definition.defaultValue() == null || definition.defaultValue().isBlank()) return;
        valueValidator.validate(definition.snapshot(), definition.defaultValue());
    }

    private DefinitionRecord requireDefinition(String workspaceKey, Long fieldId) {
        return repository.findDefinition(workspaceKey, fieldId)
                .orElseThrow(() -> new BusinessException(404, "自定义字段不存在"));
    }

    private CustomFieldType parseImplementedType(String rawType) {
        try {
            CustomFieldType type = CustomFieldType.fromWireName(rawType);
            if (!type.isImplemented()) throw new IllegalArgumentException();
            return type;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "字段类型尚未开放或不受支持");
        }
    }

    private String fieldCode(String requested) {
        String code = requested == null || requested.isBlank()
                ? "custom_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : requested.trim();
        if (!FIELD_CODE.matcher(code).matches()) throw new BusinessException(400, "字段编码格式不合法");
        return code;
    }

    private boolean supportsGrouping(CustomFieldType type) {
        return type != CustomFieldType.LONG_TEXT && type != CustomFieldType.MULTI_SELECT
                && type != CustomFieldType.URL && type != CustomFieldType.IMAGE
                && type != CustomFieldType.ATTACHMENT;
    }

    private void validateFileReferences(Map<DefinitionRecord, ValidatedCustomFieldValue> values) {
        Set<Long> fileIds = new java.util.LinkedHashSet<>();
        values.forEach((definition, value) -> {
            if (definition.fieldType() == CustomFieldType.IMAGE || definition.fieldType() == CustomFieldType.ATTACHMENT) {
                fileIds.addAll(fileIds(value.jsonValue()));
            }
        });
        if (fileIds.isEmpty()) return;
        Map<Long, FileMetadata> metadata = repository.findFileMetadata(fileIds);
        for (Map.Entry<DefinitionRecord, ValidatedCustomFieldValue> entry : values.entrySet()) {
            CustomFieldType type = entry.getKey().fieldType();
            if (type != CustomFieldType.IMAGE && type != CustomFieldType.ATTACHMENT) continue;
            for (Long fileId : fileIds(entry.getValue().jsonValue())) {
                FileMetadata file = metadata.get(fileId);
                if (file == null) {
                    throw new BusinessException(400, entry.getKey().fieldName() + "包含不存在或不可用的文件");
                }
                if (!fileMatchesType(file, type)) {
                    throw new BusinessException(400, entry.getKey().fieldName() + "包含不支持的文件类型");
                }
            }
        }
    }

    private List<Long> fileIds(Object value) {
        Object source = value;
        if (source instanceof Map<?, ?> map) source = map.get("files");
        if (!(source instanceof java.util.Collection<?> collection)) return List.of();
        List<Long> result = new ArrayList<>();
        for (Object item : collection) {
            Object rawId = item instanceof Map<?, ?> map
                    ? (map.get("fileId") == null ? map.get("id") : map.get("fileId"))
                    : item;
            if (rawId == null) continue;
            try {
                long id = Long.parseLong(String.valueOf(rawId));
                if (id > 0) result.add(id);
            } catch (NumberFormatException ignored) {
                // Legacy URL values have no file id and remain readable.
            }
        }
        return result;
    }

    private boolean fileMatchesType(FileMetadata file, CustomFieldType type) {
        String name = file.name() == null ? "" : file.name().toLowerCase(java.util.Locale.ROOT);
        String mime = file.mimeType() == null ? "" : file.mimeType().toLowerCase(java.util.Locale.ROOT);
        Set<String> imageExtensions = Set.of(".jpg", ".jpeg", ".png", ".webp");
        Set<String> imageMimeTypes = Set.of("image/jpeg", "image/png", "image/webp");
        boolean image = imageExtensions.stream().anyMatch(name::endsWith) && imageMimeTypes.contains(mime);
        if (type == CustomFieldType.IMAGE) return image;
        Set<String> attachmentExtensions = Set.of(
                ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv",
                ".jpg", ".jpeg", ".png", ".webp", ".zip"
        );
        Set<String> attachmentMimeTypes = Set.of(
                "application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain", "text/csv", "image/jpeg", "image/png", "image/webp",
                "application/zip", "application/x-zip-compressed"
        );
        return attachmentExtensions.stream().anyMatch(name::endsWith) && attachmentMimeTypes.contains(mime);
    }

    private boolean supportsUniqueValue(CustomFieldType type) {
        return type != CustomFieldType.LONG_TEXT && type != CustomFieldType.MULTI_SELECT
                && type != CustomFieldType.IMAGE && type != CustomFieldType.ATTACHMENT;
    }

    private Object optionsInput(Map<String, Object> body, DefinitionRecord current) {
        if (body != null && body.containsKey("options")) return body.get("options");
        if (current == null) return null;
        return current.options().stream().map(option -> Map.of(
                "optionCode", option.optionCode(),
                "optionLabel", option.optionLabel(),
                "color", option.color() == null ? "" : option.color(),
                "displayOrder", option.displayOrder()
        )).toList();
    }

    private String legacyWireType(CustomFieldType type) {
        return switch (type) {
            case CURRENCY -> "money";
            case SINGLE_SELECT -> "singleSelect";
            case MULTI_SELECT -> "multiSelect";
            case BOOLEAN -> "checkbox";
            case DATETIME -> "datetime";
            default -> type.name().toLowerCase(Locale.ROOT).replace('_', '-');
        };
    }

    private Long operatorId(AuthPrincipal principal) {
        return principal == null || principal.userId() == null ? 0L : principal.userId();
    }

    private String requestId(String requestId) {
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.substring(0, Math.min(96, requestId.length()));
    }

    private String requiredText(Map<String, Object> body, String fallback, String... keys) {
        String value = stringValue(body, keys);
        if (value == null) value = fallback;
        return cleanLabel(value, 100, "字段名称");
    }

    private String cleanLabel(String value, int maxLength, String label) {
        if (value == null || value.trim().isBlank()) throw new BusinessException(400, label + "不能为空");
        String cleaned = value.trim();
        if (cleaned.length() > maxLength) throw new BusinessException(400, label + "长度超过限制");
        if (cleaned.contains("<") || cleaned.contains(">")) throw new BusinessException(400, label + "包含不允许的字符");
        return cleaned;
    }

    private String text(Map<String, Object> body, String fallback, int maxLength, String key) {
        String value = stringValue(body, key);
        if (value == null) return fallback;
        String cleaned = value.trim();
        if (cleaned.length() > maxLength) throw new BusinessException(400, key + "长度超过限制");
        return cleaned;
    }

    private String stringValue(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }

    private boolean boolValue(Map<String, Object> body, String key, boolean fallback) {
        Object value = body.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Map<String, Object> body, String key, int fallback) {
        Object value = body.get(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, key + "必须为整数");
        }
    }

    private long longValue(Map<String, Object> body, String key, long fallback) {
        Object value = body.get(key);
        if (value == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, key + "必须为整数");
        }
    }

    private Long requiredLong(Map<String, Object> body, String... keys) {
        String value = stringValue(body, keys);
        if (value == null) throw new BusinessException(400, "缺少业务记录 ID");
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "业务记录 ID 不合法");
        }
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new BusinessException(400, "字段配置不是有效 JSON");
        }
    }
}

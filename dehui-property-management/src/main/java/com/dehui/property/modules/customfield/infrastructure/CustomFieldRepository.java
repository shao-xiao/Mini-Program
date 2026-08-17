package com.dehui.property.modules.customfield.infrastructure;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.customfield.domain.CustomFieldDefinitionSnapshot;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.customfield.domain.ValidatedCustomFieldValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CustomFieldRepository {

    // Legacy mobile hydration used "/api/mobile/files/" + id; V50 also serves the
    // authenticated /api/files/{id} path with the same record-scope checks.

    private static final String DEFINITION_SELECT = """
            SELECT id, code, workspace_key, entity_type, field_key, label, field_type,
                   description, required_flag, unique_value, default_value, config_json,
                   options_json, sort_order, sortable, groupable, status, version
            FROM custom_field_definition
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CustomFieldRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public int countActiveDefinitions(String workspaceKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition WHERE workspace_key = ? AND deleted = 0",
                Integer.class,
                workspaceKey
        );
        return count == null ? 0 : count;
    }

    public List<DefinitionRecord> findDefinitions(String workspaceKey, boolean enabledOnly) {
        String statusClause = enabledOnly ? " AND status = 'ENABLED'" : "";
        List<DefinitionRecord> definitions = jdbcTemplate.query(
                DEFINITION_SELECT + " WHERE workspace_key = ? AND deleted = 0" + statusClause
                        + " ORDER BY sort_order ASC, id ASC",
                (resultSet, rowNum) -> definitionRecord(resultSet),
                workspaceKey
        );
        attachOptions(definitions);
        return definitions;
    }

    public Optional<DefinitionRecord> findDefinition(String workspaceKey, Long fieldId) {
        List<DefinitionRecord> rows = jdbcTemplate.query(
                DEFINITION_SELECT + " WHERE workspace_key = ? AND id = ? AND deleted = 0",
                (resultSet, rowNum) -> definitionRecord(resultSet),
                workspaceKey,
                fieldId
        );
        attachOptions(rows);
        return rows.stream().findFirst();
    }

    public Optional<DefinitionRecord> findDefinition(String workspaceKey, String fieldCode) {
        List<DefinitionRecord> rows = jdbcTemplate.query(
                DEFINITION_SELECT + " WHERE workspace_key = ? AND field_key = ? AND deleted = 0",
                (resultSet, rowNum) -> definitionRecord(resultSet),
                workspaceKey,
                fieldCode
        );
        attachOptions(rows);
        return rows.stream().findFirst();
    }

    public Long insertDefinition(DefinitionWrite write, Long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO custom_field_definition
                            (code, module, workspace_key, entity_type, field_key, label, description,
                             required_flag, unique_value, field_type, options_json, default_value,
                             config_json, sort_order, sortable, groupable, status, version,
                             created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED', 0, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS
                );
                int index = 1;
                statement.setString(index++, "CF-" + write.workspaceKey() + "-" + write.fieldCode());
                statement.setString(index++, write.workspaceKey());
                statement.setString(index++, write.workspaceKey());
                statement.setString(index++, write.entityType());
                statement.setString(index++, write.fieldCode());
                statement.setString(index++, write.fieldName());
                statement.setString(index++, write.description());
                statement.setBoolean(index++, write.required());
                statement.setBoolean(index++, write.uniqueValue());
                statement.setString(index++, write.fieldType().name());
                statement.setString(index++, write.legacyOptionsJson());
                statement.setString(index++, write.defaultValue());
                statement.setString(index++, write.configJson());
                statement.setInt(index++, write.displayOrder());
                statement.setBoolean(index++, write.sortable());
                statement.setBoolean(index++, write.groupable());
                statement.setLong(index++, operatorId);
                statement.setLong(index, operatorId);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "自定义字段编码已存在");
        }
        Number key = keyHolder.getKey();
        if (key == null) throw new BusinessException(500, "自定义字段创建失败");
        return key.longValue();
    }

    public void updateDefinition(Long fieldId, long expectedVersion, DefinitionWrite write, Long operatorId) {
        int updated;
        try {
            updated = jdbcTemplate.update(
                    """
                    UPDATE custom_field_definition
                    SET label = ?, description = ?, required_flag = ?, unique_value = ?,
                        field_type = ?, options_json = ?, default_value = ?, config_json = ?, sort_order = ?, sortable = ?,
                        groupable = ?, version = version + 1, updated_by = ?
                    WHERE id = ? AND workspace_key = ? AND deleted = 0 AND version = ?
                    """,
                    write.fieldName(), write.description(), write.required(), write.uniqueValue(),
                    write.fieldType().name(), write.legacyOptionsJson(), write.defaultValue(), write.configJson(), write.displayOrder(), write.sortable(),
                    write.groupable(), operatorId, fieldId, write.workspaceKey(), expectedVersion
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "自定义字段名称或编码冲突");
        }
        if (updated != 1) throw new BusinessException(409, "字段定义已被其他用户修改，请刷新后重试");
    }

    public void replaceOptions(Long fieldId, List<OptionWrite> options, Long operatorId) {
        jdbcTemplate.update(
                "UPDATE custom_field_option SET deleted = 1, status = 'DISABLED', updated_by = ? WHERE field_id = ?",
                operatorId,
                fieldId
        );
        for (OptionWrite option : options) {
            jdbcTemplate.update(
                    """
                    INSERT INTO custom_field_option
                        (field_id, option_code, option_label, color, display_order, status, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, 'ENABLED', ?, ?)
                    ON DUPLICATE KEY UPDATE option_label = VALUES(option_label), color = VALUES(color),
                        display_order = VALUES(display_order), status = 'ENABLED', deleted = 0,
                        updated_by = VALUES(updated_by)
                    """,
                    fieldId, option.optionCode(), option.optionLabel(), option.color(), option.displayOrder(),
                    operatorId, operatorId
            );
        }
    }

    public void updateStatus(String workspaceKey, Long fieldId, String status, boolean deleted, Long operatorId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE custom_field_definition
                SET status = ?, deleted = ?, version = version + 1, updated_by = ?
                WHERE workspace_key = ? AND id = ? AND deleted = 0
                """,
                status, deleted, operatorId, workspaceKey, fieldId
        );
        if (updated != 1) throw new BusinessException(404, "自定义字段不存在");
    }

    public void updateOrder(String workspaceKey, Long fieldId, int displayOrder, Long operatorId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE custom_field_definition
                SET sort_order = ?, version = version + 1, updated_by = ?
                WHERE workspace_key = ? AND id = ? AND deleted = 0
                """,
                displayOrder, operatorId, workspaceKey, fieldId
        );
        if (updated != 1) throw new BusinessException(404, "自定义字段不存在");
    }

    public boolean hasValues(Long fieldId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custom_field_value WHERE field_id = ? AND deleted = 0",
                Integer.class,
                fieldId
        );
        return count != null && count > 0;
    }

    public boolean hasMissingValues(CustomFieldWorkspaceRegistry.Registration workspace, Long fieldId) {
        String sql = "SELECT COUNT(*) FROM " + workspace.tableName() + " entity_record "
                + "LEFT JOIN custom_field_value field_value ON field_value.entity_id = entity_record."
                + workspace.idColumn() + " AND field_value.field_id = ? AND field_value.deleted = 0 "
                + "WHERE entity_record.deleted = 0 AND field_value.id IS NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fieldId);
        return count != null && count > 0;
    }

    public boolean uniqueValueExists(
            DefinitionRecord definition,
            Long excludedEntityId,
            ValidatedCustomFieldValue value
    ) {
        String column = definition.fieldType().storageColumn();
        Object typedValue = switch (definition.fieldType()) {
            case TEXT, PHONE, EMAIL, URL, SINGLE_SELECT -> value.textValue();
            case NUMBER -> value.numberValue();
            case CURRENCY -> value.decimalValue();
            case DATE -> value.dateValue() == null ? null : Date.valueOf(value.dateValue());
            case DATETIME -> value.datetimeValue() == null ? null : Timestamp.valueOf(value.datetimeValue());
            case BOOLEAN -> value.booleanValue();
            default -> null;
        };
        if (typedValue == null) return false;
        List<Long> conflicts = jdbcTemplate.queryForList(
                "SELECT entity_id FROM custom_field_value WHERE field_id = ? AND entity_id <> ? "
                        + "AND deleted = 0 AND " + column + " = ? LIMIT 1 FOR UPDATE",
                Long.class,
                definition.id(), excludedEntityId, typedValue
        );
        return !conflicts.isEmpty();
    }

    public boolean entityExists(CustomFieldWorkspaceRegistry.Registration workspace, Long entityId) {
        return lockEntitiesForUpdate(workspace, List.of(entityId)).contains(entityId);
    }

    public Set<Long> lockEntitiesForUpdate(
            CustomFieldWorkspaceRegistry.Registration workspace,
            Collection<Long> entityIds) {
        List<Long> orderedIds = entityIds == null ? List.of() : entityIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();
        if (orderedIds.isEmpty()) return Set.of();
        String placeholders = orderedIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        String sql = "SELECT " + workspace.idColumn() + " FROM " + workspace.tableName()
                + " WHERE " + workspace.idColumn() + " IN (" + placeholders + ") AND deleted = 0"
                + " ORDER BY " + workspace.idColumn() + " FOR UPDATE";
        return new java.util.LinkedHashSet<>(jdbcTemplate.queryForList(sql, Long.class, orderedIds.toArray()));
    }

    public Map<Long, Map<String, Object>> findValues(String workspaceKey, Collection<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) return Map.of();
        String placeholders = entityIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Object> arguments = new ArrayList<>();
        arguments.add(workspaceKey);
        arguments.addAll(entityIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT value_record.entity_id, value_record.field_key, definition.field_type,
                       value_record.value_json, value_record.value_text, value_record.value_long_text,
                       value_record.value_number, value_record.value_decimal, value_record.value_date,
                       value_record.value_datetime, value_record.value_boolean
                FROM custom_field_value value_record
                JOIN custom_field_definition definition ON definition.id = value_record.field_id
                WHERE value_record.workspace_key = ? AND value_record.deleted = 0
                  AND definition.deleted = 0 AND definition.status = 'ENABLED'
                  AND value_record.entity_id IN (
                """ + placeholders + ")",
                arguments.toArray()
        );
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        Map<Long, Map<String, CustomFieldType>> valueTypes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long entityId = ((Number) row.get("entity_id")).longValue();
            String fieldKey = String.valueOf(row.get("field_key"));
            CustomFieldType fieldType = CustomFieldType.fromWireName(String.valueOf(row.get("field_type")));
            result.computeIfAbsent(entityId, ignored -> new LinkedHashMap<>())
                    .put(fieldKey, readTypedValue(row));
            valueTypes.computeIfAbsent(entityId, ignored -> new LinkedHashMap<>())
                    .put(fieldKey, fieldType);
        }
        Set<Long> fileIds = new java.util.LinkedHashSet<>();
        result.forEach((entityId, values) -> values.forEach((fieldKey, value) -> {
            CustomFieldType fieldType = valueTypes.getOrDefault(entityId, Map.of()).get(fieldKey);
            if (fieldType == CustomFieldType.IMAGE || fieldType == CustomFieldType.ATTACHMENT) {
                collectFileIds(value, fileIds);
            }
        }));
        Map<Long, FileMetadata> files = findFileMetadata(fileIds);
        result.forEach((entityId, values) -> values.replaceAll((fieldKey, value) -> {
            CustomFieldType fieldType = valueTypes.getOrDefault(entityId, Map.of()).get(fieldKey);
            return fieldType == CustomFieldType.IMAGE || fieldType == CustomFieldType.ATTACHMENT
                    ? hydrateFileValue(value, files)
                    : value;
        }));
        return result;
    }

    public Map<Long, FileMetadata> findFileMetadata(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return Map.of();
        List<Long> safeIds = fileIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (safeIds.isEmpty()) return Map.of();
        String placeholders = safeIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, original_name, storage_path, access_scope, mime_type, file_size "
                        + "FROM file_upload WHERE deleted = 0 AND status = 'ENABLED' AND id IN (" + placeholders + ")",
                safeIds.toArray()
        );
        Map<Long, FileMetadata> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String url = "INTERNAL".equals(String.valueOf(row.get("access_scope")))
                    ? "/api/files/" + id
                    : normalizePublicUrl(String.valueOf(row.getOrDefault("storage_path", "")));
            result.put(id, new FileMetadata(
                    id,
                    String.valueOf(row.getOrDefault("original_name", "")),
                    url,
                    String.valueOf(row.getOrDefault("mime_type", "")),
                    row.get("file_size") instanceof Number number ? number.longValue() : 0L
            ));
        }
        return result;
    }

    public void claimInternalFiles(
            CustomFieldWorkspaceRegistry.Registration workspace,
            Long entityId,
            Collection<Long> fileIds,
            Long operatorId
    ) {
        if (fileIds == null || fileIds.isEmpty()) return;
        List<Long> ids = fileIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) return;
        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Object> countArgs = new ArrayList<>(ids);
        countArgs.add(workspace.workspaceKey());
        countArgs.add(operatorId);
        countArgs.add(entityId);
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM file_upload
                WHERE id IN (%s) AND status = 'ENABLED' AND deleted = 0
                  AND (
                    access_scope <> 'INTERNAL'
                    OR (
                      workspace_key = ?
                      AND ((biz_id IS NULL AND owner_sys_user_id = ?) OR biz_id = ?)
                    )
                  )
                """.formatted(placeholders),
                Long.class,
                countArgs.toArray()
        );
        if (count == null || count != ids.size()) {
            throw new BusinessException(400, "文件不存在、已被使用或不属于当前工作区");
        }
        List<Object> updateArgs = new ArrayList<>();
        updateArgs.add(workspace.workspaceKey());
        updateArgs.add(entityId);
        updateArgs.add(operatorId);
        updateArgs.addAll(ids);
        jdbcTemplate.update(
                """
                UPDATE file_upload
                SET biz_type = 'CUSTOM_FIELD', workspace_key = ?, biz_id = ?, updated_by = ?
                WHERE id IN (%s) AND access_scope = 'INTERNAL' AND deleted = 0
                """.formatted(placeholders),
                updateArgs.toArray()
        );
    }

    public void replaceValues(
            CustomFieldWorkspaceRegistry.Registration workspace,
            Long entityId,
            Map<DefinitionRecord, ValidatedCustomFieldValue> values,
            Long operatorId
    ) {
        jdbcTemplate.update(
                "UPDATE custom_field_value SET deleted = 1, version = version + 1, updated_by = ? "
                        + "WHERE workspace_key = ? AND entity_id = ? AND deleted = 0",
                operatorId,
                workspace.workspaceKey(),
                entityId
        );
        values.forEach((definition, value) -> upsertValue(workspace, entityId, definition, value, operatorId));
    }

    public void upsertValues(
            CustomFieldWorkspaceRegistry.Registration workspace,
            Long entityId,
            Map<DefinitionRecord, ValidatedCustomFieldValue> values,
            Long operatorId
    ) {
        values.forEach((definition, value) -> upsertValue(workspace, entityId, definition, value, operatorId));
    }

    public void addHistory(
            Long fieldId,
            String operation,
            Object beforeSnapshot,
            Object afterSnapshot,
            Long operatorId,
            String requestId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO custom_field_definition_history
                    (field_id, operation, before_snapshot, after_snapshot, operator_id, request_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                fieldId, operation, writeJson(beforeSnapshot), writeJson(afterSnapshot), operatorId, requestId
        );
    }

    private void upsertValue(
            CustomFieldWorkspaceRegistry.Registration workspace,
            Long entityId,
            DefinitionRecord definition,
            ValidatedCustomFieldValue value,
            Long operatorId
    ) {
        if (value.equals(ValidatedCustomFieldValue.empty())) return;
        try {
            jdbcTemplate.update(
                    """
                INSERT INTO custom_field_value
                    (field_id, module, workspace_key, entity_type, record_id, entity_id, field_key,
                     value_json, value_text, value_long_text, value_number, value_decimal,
                     value_date, value_datetime, value_boolean, version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                ON DUPLICATE KEY UPDATE field_id = VALUES(field_id), workspace_key = VALUES(workspace_key),
                    entity_type = VALUES(entity_type), entity_id = VALUES(entity_id),
                    value_json = VALUES(value_json), value_text = VALUES(value_text),
                    value_long_text = VALUES(value_long_text), value_number = VALUES(value_number),
                    value_decimal = VALUES(value_decimal), value_date = VALUES(value_date),
                    value_datetime = VALUES(value_datetime), value_boolean = VALUES(value_boolean),
                    version = version + 1, deleted = 0, updated_by = VALUES(updated_by)
                    """,
                    definition.id(), workspace.workspaceKey(), workspace.workspaceKey(), workspace.entityType(),
                    entityId, entityId, definition.fieldCode(), writeJson(value.jsonValue()), value.textValue(),
                    value.longTextValue(), value.numberValue(), value.decimalValue(),
                    value.dateValue() == null ? null : Date.valueOf(value.dateValue()),
                    value.datetimeValue() == null ? null : Timestamp.valueOf(value.datetimeValue()),
                    value.booleanValue(), operatorId, operatorId
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, definition.fieldName() + "已被其他请求更新，请刷新后重试");
        }
    }

    private void attachOptions(List<DefinitionRecord> definitions) {
        if (definitions.isEmpty()) return;
        String placeholders = definitions.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Long> fieldIds = definitions.stream().map(DefinitionRecord::id).toList();
        Map<Long, List<OptionRecord>> optionsByField = jdbcTemplate.query(
                """
                SELECT id, field_id, option_code, option_label, color, display_order, status
                FROM custom_field_option
                WHERE deleted = 0 AND field_id IN (
                """ + placeholders + ") ORDER BY display_order ASC, id ASC",
                resultSet -> {
                    Map<Long, List<OptionRecord>> result = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        Long fieldId = resultSet.getLong("field_id");
                        result.computeIfAbsent(fieldId, ignored -> new ArrayList<>()).add(new OptionRecord(
                                resultSet.getLong("id"),
                                resultSet.getString("option_code"),
                                resultSet.getString("option_label"),
                                resultSet.getString("color"),
                                resultSet.getInt("display_order"),
                                resultSet.getString("status")
                        ));
                    }
                    return result;
                },
                fieldIds.toArray()
        );
        for (int index = 0; index < definitions.size(); index++) {
            DefinitionRecord definition = definitions.get(index);
            List<OptionRecord> options = optionsByField.getOrDefault(definition.id(), List.of());
            if (options.isEmpty()) options = legacyOptions(definition.legacyOptionsJson());
            definitions.set(index, definition.withOptions(options));
        }
    }

    private DefinitionRecord definitionRecord(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new DefinitionRecord(
                resultSet.getLong("id"), resultSet.getString("code"), resultSet.getString("workspace_key"),
                resultSet.getString("entity_type"), resultSet.getString("field_key"), resultSet.getString("label"),
                CustomFieldType.fromWireName(resultSet.getString("field_type")), resultSet.getString("description"),
                resultSet.getBoolean("required_flag"), resultSet.getBoolean("unique_value"),
                resultSet.getString("default_value"), readMap(resultSet.getString("config_json")),
                resultSet.getString("options_json"), resultSet.getInt("sort_order"),
                resultSet.getBoolean("sortable"), resultSet.getBoolean("groupable"),
                resultSet.getString("status"), resultSet.getLong("version"), List.of()
        );
    }

    private Object readTypedValue(Map<String, Object> row) {
        CustomFieldType type = CustomFieldType.fromWireName(String.valueOf(row.get("field_type")));
        Object value = switch (type) {
            case TEXT, PHONE, EMAIL, URL, SINGLE_SELECT -> row.get("value_text");
            case LONG_TEXT -> row.get("value_long_text");
            case NUMBER -> row.get("value_number");
            case CURRENCY -> row.get("value_decimal");
            case DATE -> row.get("value_date");
            case DATETIME -> row.get("value_datetime");
            case BOOLEAN -> booleanValue(row.get("value_boolean"));
            case MULTI_SELECT, IMAGE, ATTACHMENT -> readJson(row.get("value_json"));
            default -> null;
        };
        return value != null ? value : readJson(row.get("value_json"));
    }

    private void collectFileIds(Object value, Set<Long> target) {
        for (Object item : fileItems(value)) {
            Long fileId = fileId(item);
            if (fileId != null) target.add(fileId);
        }
    }

    private Object hydrateFileValue(Object value, Map<Long, FileMetadata> metadata) {
        List<Map<String, Object>> hydrated = new ArrayList<>();
        int sortOrder = 0;
        for (Object item : fileItems(value)) {
            Long fileId = fileId(item);
            if (fileId != null) {
                FileMetadata file = metadata.get(fileId);
                if (file == null) continue;
                LinkedHashMap<String, Object> result = new LinkedHashMap<>();
                result.put("fileId", file.id());
                result.put("name", file.name());
                result.put("url", file.url());
                result.put("mimeType", file.mimeType());
                result.put("size", file.size());
                result.put("sortOrder", sortOrder++);
                hydrated.add(result);
                continue;
            }
            if (item instanceof Map<?, ?> map && map.get("url") != null) {
                LinkedHashMap<String, Object> legacy = new LinkedHashMap<>();
                Object legacyName = map.get("name");
                Object legacyMimeType = map.get("mimeType");
                Object legacySize = map.get("size");
                legacy.put("fileId", null);
                legacy.put("name", legacyName == null ? "历史文件" : legacyName);
                legacy.put("url", normalizePublicUrl(String.valueOf(map.get("url"))));
                legacy.put("mimeType", legacyMimeType == null ? "" : legacyMimeType);
                legacy.put("size", legacySize == null ? 0 : legacySize);
                legacy.put("sortOrder", sortOrder++);
                legacy.put("legacy", true);
                hydrated.add(legacy);
            } else if (item instanceof String text && !text.isBlank()) {
                LinkedHashMap<String, Object> legacy = new LinkedHashMap<>();
                legacy.put("fileId", null);
                legacy.put("name", text.substring(Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\')) + 1));
                legacy.put("url", normalizePublicUrl(text));
                legacy.put("mimeType", "");
                legacy.put("size", 0);
                legacy.put("sortOrder", sortOrder++);
                legacy.put("legacy", true);
                hydrated.add(legacy);
            }
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("version", 1);
        result.put("files", hydrated);
        return result;
    }

    private Collection<?> fileItems(Object value) {
        if (value instanceof Map<?, ?> map && map.get("files") instanceof Collection<?> files) return files;
        if (value instanceof Collection<?> collection) return collection;
        return value == null ? List.of() : List.of(value);
    }

    private Long fileId(Object item) {
        Object value = item;
        if (item instanceof Map<?, ?> map) value = map.get("fileId") != null ? map.get("fileId") : map.get("id");
        if (value == null) return null;
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizePublicUrl(String url) {
        return url.replace("/api/files/public/", "/files/public/");
    }

    private List<OptionRecord> legacyOptions(String json) {
        Object value = readJson(json);
        if (!(value instanceof List<?> list)) return List.of();
        List<OptionRecord> options = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            String label = String.valueOf(list.get(index));
            options.add(new OptionRecord(null, label, label, null, index, "ENABLED"));
        }
        return options;
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Object readJson(Object json) {
        if (json == null || String.valueOf(json).isBlank()) return null;
        try {
            return objectMapper.readValue(String.valueOf(json), Object.class);
        } catch (Exception ignored) {
            return json;
        }
    }

    private Boolean booleanValue(Object rawValue) {
        if (rawValue == null) return null;
        if (rawValue instanceof Boolean bool) return bool;
        if (rawValue instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(rawValue));
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(400, "自定义字段 JSON 数据不合法");
        }
    }

    public record FileMetadata(Long id, String name, String url, String mimeType, long size) {}

    public record DefinitionRecord(
            Long id,
            String code,
            String workspaceKey,
            String entityType,
            String fieldCode,
            String fieldName,
            CustomFieldType fieldType,
            String description,
            boolean required,
            boolean uniqueValue,
            String defaultValue,
            Map<String, Object> config,
            String legacyOptionsJson,
            int displayOrder,
            boolean sortable,
            boolean groupable,
            String status,
            long version,
            List<OptionRecord> options
    ) {
        DefinitionRecord withOptions(List<OptionRecord> nextOptions) {
            return new DefinitionRecord(id, code, workspaceKey, entityType, fieldCode, fieldName, fieldType,
                    description, required, uniqueValue, defaultValue, config, legacyOptionsJson, displayOrder,
                    sortable, groupable, status, version, List.copyOf(nextOptions));
        }

        public CustomFieldDefinitionSnapshot snapshot() {
            Set<String> optionCodes = options.stream()
                    .filter(option -> "ENABLED".equals(option.status()))
                    .map(OptionRecord::optionCode)
                    .collect(Collectors.toUnmodifiableSet());
            return new CustomFieldDefinitionSnapshot(id, workspaceKey, entityType, fieldCode, fieldName,
                    fieldType, required, uniqueValue, config, optionCodes, version);
        }
    }

    public record OptionRecord(
            Long id,
            String optionCode,
            String optionLabel,
            String color,
            int displayOrder,
            String status
    ) {
    }

    public record DefinitionWrite(
            String workspaceKey,
            String entityType,
            String fieldCode,
            String fieldName,
            CustomFieldType fieldType,
            String description,
            boolean required,
            boolean uniqueValue,
            String defaultValue,
            String configJson,
            String legacyOptionsJson,
            int displayOrder,
            boolean sortable,
            boolean groupable
    ) {
    }

    public record OptionWrite(String optionCode, String optionLabel, String color, int displayOrder) {
    }
}

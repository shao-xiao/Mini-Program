package com.dehui.property.modules.customfield.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.customfield.domain.CustomFieldValueValidator;
import com.dehui.property.modules.customfield.domain.ValidatedCustomFieldValue;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.DefinitionRecord;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.DefinitionWrite;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomFieldImportService {

    private static final long SYSTEM_OPERATOR_ID = 0L;

    private final CustomFieldWorkspaceRegistry registry;
    private final CustomFieldRepository repository;
    private final CustomFieldValueValidator valueValidator = new CustomFieldValueValidator();

    public CustomFieldImportService(
            CustomFieldWorkspaceRegistry registry,
            CustomFieldRepository repository
    ) {
        this.registry = registry;
        this.repository = repository;
    }

    @Transactional
    public void ensureDefinitions(String workspaceKey, List<ImportFieldDefinition> fields) {
        var workspace = registry.require(workspaceKey);
        if (fields == null || fields.isEmpty()) return;

        for (ImportFieldDefinition field : fields) {
            DefinitionRecord existing = repository.findDefinition(workspaceKey, field.fieldCode()).orElse(null);
            if (existing != null) {
                requireCompatibleDefinition(existing, field);
                continue;
            }
            if (repository.countActiveDefinitions(workspaceKey) >= workspace.maximumCustomFields()) {
                throw new BusinessException(409, "当前工作区自定义字段数量已达上限");
            }
            DefinitionWrite write = new DefinitionWrite(
                    workspace.workspaceKey(), workspace.entityType(), field.fieldCode(), field.fieldName(),
                    field.fieldType(), field.description(), false, false, null, "{}", "[]",
                    field.displayOrder(), field.sortable(), field.groupable()
            );
            Long fieldId;
            try {
                fieldId = repository.insertDefinition(write, SYSTEM_OPERATOR_ID);
            } catch (BusinessException exception) {
                DefinitionRecord raced = repository.findDefinition(workspaceKey, field.fieldCode()).orElse(null);
                if (raced == null) throw exception;
                requireCompatibleDefinition(raced, field);
                continue;
            }
            DefinitionRecord created = repository.findDefinition(workspaceKey, fieldId)
                    .orElseThrow(() -> new BusinessException(500, "导入字段定义创建后读取失败"));
            repository.addHistory(
                    fieldId,
                    "IMPORT_CREATE",
                    null,
                    Map.of(
                            "workspaceKey", created.workspaceKey(),
                            "fieldCode", created.fieldCode(),
                            "fieldName", created.fieldName(),
                            "fieldType", created.fieldType().name()
                    ),
                    SYSTEM_OPERATOR_ID,
                    "import-" + UUID.randomUUID()
            );
        }
    }

    @Transactional
    public void saveValues(String workspaceKey, Long entityId, Map<String, Object> values) {
        var workspace = registry.require(workspaceKey);
        if (entityId == null || entityId <= 0 || !repository.entityExists(workspace, entityId)) {
            throw new BusinessException(404, "导入记录不存在");
        }
        if (values == null || values.isEmpty()) return;

        Map<DefinitionRecord, ValidatedCustomFieldValue> validated = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (isBlank(entry.getValue())) continue;
            DefinitionRecord definition = repository.findDefinition(workspaceKey, entry.getKey())
                    .orElseThrow(() -> new BusinessException(400, "导入字段未注册: " + entry.getKey()));
            ValidatedCustomFieldValue value = valueValidator.validate(definition.snapshot(), entry.getValue());
            if (definition.uniqueValue() && repository.uniqueValueExists(definition, entityId, value)) {
                throw new BusinessException(409, definition.fieldName() + "的值已存在");
            }
            validated.put(definition, value);
        }
        repository.upsertValues(workspace, entityId, validated, SYSTEM_OPERATOR_ID);
    }

    @Transactional
    public void saveImportedRow(
            String workspaceKey,
            Long entityId,
            Map<String, Object> importedRow,
            Map<String, Object> aliases
    ) {
        Map<String, Object> values = new LinkedHashMap<>(repository.findValues(workspaceKey, List.of(entityId))
                .getOrDefault(entityId, Map.of()));
        if (aliases != null) aliases.forEach((fieldCode, value) -> {
            if (!isBlank(value)) values.put(fieldCode, value);
        });
        Map<String, Object> row = importedRow == null ? Map.of() : importedRow;
        for (DefinitionRecord definition : repository.findDefinitions(workspaceKey, true)) {
            if (row.containsKey(definition.fieldCode()) && !isBlank(row.get(definition.fieldCode()))) {
                values.put(definition.fieldCode(), row.get(definition.fieldCode()));
            } else if (row.containsKey(definition.fieldName()) && !isBlank(row.get(definition.fieldName()))) {
                values.put(definition.fieldCode(), row.get(definition.fieldName()));
            }
        }
        saveValues(workspaceKey, entityId, values);
    }

    public List<ImportFieldHeader> enabledHeaders(String workspaceKey) {
        registry.require(workspaceKey);
        return repository.findDefinitions(workspaceKey, true).stream()
                .map(definition -> new ImportFieldHeader(
                        definition.fieldCode(),
                        definition.fieldName(),
                        definition.fieldType(),
                        definition.options().stream().collect(Collectors.toMap(
                                CustomFieldRepository.OptionRecord::optionCode,
                                CustomFieldRepository.OptionRecord::optionLabel,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ))
                ))
                .toList();
    }

    private void requireCompatibleDefinition(DefinitionRecord existing, ImportFieldDefinition requested) {
        if (existing.fieldType() != requested.fieldType()) {
            throw new BusinessException(
                    409,
                    "导入字段类型冲突: " + requested.fieldCode()
                            + "，当前为 " + existing.fieldType().name()
                            + "，要求为 " + requested.fieldType().name()
            );
        }
    }

    private boolean isBlank(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    public record ImportFieldDefinition(
            String fieldCode,
            String fieldName,
            CustomFieldType fieldType,
            String description,
            int displayOrder,
            boolean sortable,
            boolean groupable
    ) {
    }

    public record ImportFieldHeader(
            String fieldCode,
            String fieldName,
            CustomFieldType fieldType,
            Map<String, String> optionLabels
    ) {
        public ImportFieldHeader {
            optionLabels = Map.copyOf(optionLabels);
        }
    }
}

package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.customfield.domain.CustomFieldValueValidator;
import com.dehui.property.modules.customfield.domain.ValidatedCustomFieldValue;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.DefinitionRecord;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates and persists custom values for an entity whose mobile data scope
 * has already been checked by the owning business controller.
 */
@Service
public class MobileCustomFieldValueService {

    private final CustomFieldWorkspaceRegistry registry;
    private final CustomFieldRepository repository;
    private final CustomFieldValueValidator validator = new CustomFieldValueValidator();

    public MobileCustomFieldValueService(
            CustomFieldWorkspaceRegistry registry,
            CustomFieldRepository repository) {
        this.registry = registry;
        this.repository = repository;
    }

    @Transactional
    public void save(String workspaceKey, Long entityId, Object rawValues, Long mobileUserId) {
        CustomFieldWorkspaceRegistry.Registration workspace = registry.require(workspaceKey);
        if (entityId == null || entityId <= 0 || !repository.entityExists(workspace, entityId)) {
            throw new BusinessException(404, "业务记录不存在或无权访问");
        }
        Map<String, Object> submitted = stringMap(rawValues);
        List<DefinitionRecord> definitions = repository.findDefinitions(workspaceKey, true);
        Set<String> fieldCodes = definitions.stream()
                .map(DefinitionRecord::fieldCode)
                .collect(java.util.stream.Collectors.toSet());
        submitted.keySet().forEach(fieldCode -> {
            if (!fieldCodes.contains(fieldCode)) {
                throw new BusinessException(400, "存在未注册的自定义字段: " + fieldCode);
            }
        });

        Map<DefinitionRecord, ValidatedCustomFieldValue> validated = new LinkedHashMap<>();
        Set<Long> fileIds = new LinkedHashSet<>();
        for (DefinitionRecord definition : definitions) {
            ValidatedCustomFieldValue value = validator.validate(
                    definition.snapshot(),
                    submitted.get(definition.fieldCode())
            );
            if (definition.uniqueValue()
                    && !value.equals(ValidatedCustomFieldValue.empty())
                    && repository.uniqueValueExists(definition, entityId, value)) {
                throw new BusinessException(409, definition.fieldName() + "的值已存在");
            }
            if (definition.fieldType() == CustomFieldType.IMAGE
                    || definition.fieldType() == CustomFieldType.ATTACHMENT) {
                collectFileIds(value.jsonValue(), fileIds);
            }
            validated.put(definition, value);
        }
        if (repository.findFileMetadata(fileIds).size() != fileIds.size()) {
            throw new BusinessException(400, "自定义字段包含无效文件ID");
        }
        repository.replaceValues(workspace, entityId, validated, mobileUserId == null ? 0L : mobileUserId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private void collectFileIds(Object value, Set<Long> result) {
        if (value instanceof Map<?, ?> map) {
            Object fileId = map.get("fileId");
            if (fileId instanceof Number number && number.longValue() > 0) {
                result.add(number.longValue());
            }
            map.values().forEach(item -> collectFileIds(item, result));
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collectFileIds(item, result));
        }
    }
}

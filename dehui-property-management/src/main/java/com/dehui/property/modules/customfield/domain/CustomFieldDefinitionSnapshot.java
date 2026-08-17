package com.dehui.property.modules.customfield.domain;

import java.util.Map;
import java.util.Set;

public record CustomFieldDefinitionSnapshot(
        Long id,
        String workspaceKey,
        String entityType,
        String fieldCode,
        String fieldName,
        CustomFieldType fieldType,
        boolean required,
        boolean uniqueValue,
        Map<String, Object> config,
        Set<String> activeOptionCodes,
        long version
) {
    public CustomFieldDefinitionSnapshot {
        config = config == null ? Map.of() : Map.copyOf(config);
        activeOptionCodes = activeOptionCodes == null ? Set.of() : Set.copyOf(activeOptionCodes);
    }
}

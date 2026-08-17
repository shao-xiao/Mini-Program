package com.dehui.property.modules.customfield.application;

import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class CustomFieldProjectionService {

    private final CustomFieldWorkspaceRegistry registry;
    private final CustomFieldRepository repository;

    public CustomFieldProjectionService(
            CustomFieldWorkspaceRegistry registry,
            CustomFieldRepository repository
    ) {
        this.registry = registry;
        this.repository = repository;
    }

    public PageResponse<Map<String, Object>> attach(
            String workspaceKey,
            PageResponse<Map<String, Object>> page
    ) {
        return new PageResponse<>(
                attach(workspaceKey, page.records()),
                page.total(),
                page.page(),
                page.pageSize()
        );
    }

    public List<Map<String, Object>> attach(
            String workspaceKey,
            List<Map<String, Object>> records
    ) {
        registry.require(workspaceKey);
        if (records == null || records.isEmpty()) return List.of();
        List<Long> entityIds = records.stream()
                .map(record -> entityId(record.get("id")))
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, Map<String, Object>> values = values(workspaceKey, entityIds);
        return records.stream().map(record -> {
            Map<String, Object> copy = new LinkedHashMap<>(record);
            Long entityId = entityId(record.get("id"));
            copy.put("customFields", entityId == null ? Map.of() : values.getOrDefault(entityId, Map.of()));
            return copy;
        }).toList();
    }

    public Map<Long, Map<String, Object>> values(String workspaceKey, Collection<Long> entityIds) {
        registry.require(workspaceKey);
        List<Long> safeIds = entityIds == null ? List.of() : entityIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (safeIds.isEmpty()) return Map.of();
        Map<Long, Map<String, Object>> values = new LinkedHashMap<>();
        for (int offset = 0; offset < safeIds.size(); offset += 200) {
            List<Long> batch = new ArrayList<>(safeIds.subList(offset, Math.min(offset + 200, safeIds.size())));
            values.putAll(repository.findValues(workspaceKey, batch));
        }
        return values;
    }

    public <T> PageResponse<T> attach(
            String workspaceKey,
            PageResponse<T> page,
            Function<T, Long> entityId,
            BiFunction<T, Map<String, Object>, T> valueAttacher
    ) {
        registry.require(workspaceKey);
        if (page.records() == null || page.records().isEmpty()) return page;
        List<Long> entityIds = page.records().stream()
                .map(entityId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, Map<String, Object>> values = values(workspaceKey, entityIds);
        List<T> records = page.records().stream()
                .map(record -> valueAttacher.apply(
                        record,
                        values.getOrDefault(entityId.apply(record), Map.of())
                ))
                .toList();
        return new PageResponse<>(records, page.total(), page.page(), page.pageSize());
    }

    private Long entityId(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

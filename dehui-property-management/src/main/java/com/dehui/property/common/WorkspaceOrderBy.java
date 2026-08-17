package com.dehui.property.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WorkspaceOrderBy {

    private static final int MAX_ORDERINGS = 8;

    private WorkspaceOrderBy() {
    }

    public static String build(
            Map<String, String> allowedFields,
            String groupBy,
            String sorts,
            String legacySortField,
            String legacySortDirection,
            String defaultExpression,
            String stableExpression
    ) {
        Objects.requireNonNull(allowedFields, "allowedFields");
        Objects.requireNonNull(defaultExpression, "defaultExpression");
        Objects.requireNonNull(stableExpression, "stableExpression");

        LinkedHashMap<String, String> orderings = new LinkedHashMap<>();
        appendSerialized(orderings, allowedFields, groupBy);
        appendSerialized(orderings, allowedFields, sorts);

        if (orderings.isEmpty()) {
            String expression = legacySortField == null ? null : allowedFields.get(legacySortField);
            if (expression == null) {
                expression = defaultExpression;
            }
            orderings.put(expression, expression + " " + direction(legacySortDirection, "DESC"));
        }

        orderings.putIfAbsent(stableExpression, stableExpression + " DESC");
        return String.join(", ", orderings.values());
    }

    private static void appendSerialized(
            LinkedHashMap<String, String> orderings,
            Map<String, String> allowedFields,
            String serialized
    ) {
        if (serialized == null || serialized.isBlank()) {
            return;
        }
        for (String token : serialized.split(",")) {
            if (orderings.size() >= MAX_ORDERINGS) {
                return;
            }
            String[] parts = token.trim().split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String field = parts[0].trim();
            String expression = allowedFields.get(field);
            String direction = direction(parts[1], null);
            if (expression == null || direction == null) {
                continue;
            }
            orderings.putIfAbsent(expression, expression + " " + direction);
        }
    }

    private static String direction(String rawDirection, String fallback) {
        if ("asc".equalsIgnoreCase(rawDirection)) {
            return "ASC";
        }
        if ("desc".equalsIgnoreCase(rawDirection)) {
            return "DESC";
        }
        return fallback;
    }
}

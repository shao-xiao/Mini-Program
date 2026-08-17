package com.dehui.property.modules.customfield.domain;

import java.util.Arrays;
import java.util.Locale;

public enum CustomFieldType {
    TEXT(true, "value_text"),
    LONG_TEXT(true, "value_long_text"),
    NUMBER(true, "value_number"),
    CURRENCY(true, "value_decimal"),
    SINGLE_SELECT(true, "value_text"),
    MULTI_SELECT(true, "value_json"),
    DATE(true, "value_date"),
    DATETIME(true, "value_datetime"),
    BOOLEAN(true, "value_boolean"),
    PHONE(true, "value_text"),
    EMAIL(true, "value_text"),
    URL(true, "value_text"),
    FORMULA(false, null),
    RELATION(false, null),
    LOOKUP(false, null),
    IMAGE(true, "value_json"),
    ATTACHMENT(true, "value_json"),
    USER(false, null),
    AUTO_NUMBER(false, null);

    private final boolean implemented;
    private final String storageColumn;

    CustomFieldType(boolean implemented, String storageColumn) {
        this.implemented = implemented;
        this.storageColumn = storageColumn;
    }

    public boolean isImplemented() {
        return implemented;
    }

    public String storageColumn() {
        if (!implemented) {
            throw new IllegalStateException(name() + " is reserved and has no storage column");
        }
        return storageColumn;
    }

    public static CustomFieldType fromWireName(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException("Custom field type is required");
        }
        String normalized = rawType.trim()
                .replace('-', '_')
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "MONEY" -> "CURRENCY";
            case "CHECKBOX" -> "BOOLEAN";
            case "DATE_TIME" -> "DATETIME";
            default -> normalized;
        };
        String candidate = normalized;
        return Arrays.stream(values())
                .filter(type -> type.name().equals(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported custom field type: " + rawType));
    }
}

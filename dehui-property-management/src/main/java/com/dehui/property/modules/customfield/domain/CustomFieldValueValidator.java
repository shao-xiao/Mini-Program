package com.dehui.property.modules.customfield.domain;

import com.dehui.property.common.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomFieldValueValidator {

    private static final int DEFAULT_TEXT_MAX_LENGTH = 500;
    private static final int DEFAULT_LONG_TEXT_MAX_LENGTH = 10_000;
    private static final int MAX_MULTI_SELECT_VALUES = 50;
    private static final int DEFAULT_MAX_ATTACHMENTS = 30;

    public ValidatedCustomFieldValue validate(CustomFieldDefinitionSnapshot definition, Object rawValue) {
        if (!definition.fieldType().isImplemented()) {
            throw invalid(definition, "字段类型暂未开放");
        }
        if (isEmpty(rawValue)) {
            if (definition.required()) {
                throw invalid(definition, "字段不能为空");
            }
            return ValidatedCustomFieldValue.empty();
        }

        return switch (definition.fieldType()) {
            case TEXT, PHONE -> text(definition, rawValue, DEFAULT_TEXT_MAX_LENGTH, false);
            case EMAIL -> email(definition, rawValue);
            case URL -> url(definition, rawValue);
            case LONG_TEXT -> text(definition, rawValue, DEFAULT_LONG_TEXT_MAX_LENGTH, true);
            case NUMBER -> number(definition, rawValue, 6, false);
            case CURRENCY -> number(definition, rawValue, 2, true);
            case SINGLE_SELECT -> singleSelect(definition, rawValue);
            case MULTI_SELECT -> multiSelect(definition, rawValue);
            case DATE -> date(definition, rawValue);
            case DATETIME -> datetime(definition, rawValue);
            case BOOLEAN -> bool(definition, rawValue);
            case IMAGE -> fileValue(definition, rawValue, 9, "图片");
            case ATTACHMENT -> fileValue(definition, rawValue, 10, "附件");
            default -> throw invalid(definition, "字段类型暂未开放");
        };
    }

    private ValidatedCustomFieldValue text(
            CustomFieldDefinitionSnapshot definition,
            Object rawValue,
            int defaultMaxLength,
            boolean longText
    ) {
        String value = String.valueOf(rawValue);
        int maxLength = configuredInteger(definition, "maxLength", defaultMaxLength);
        if (value.length() > maxLength) {
            throw invalid(definition, "长度不能超过 " + maxLength);
        }
        return longText
                ? new ValidatedCustomFieldValue(value, null, value, null, null, null, null, null)
                : new ValidatedCustomFieldValue(value, value, null, null, null, null, null, null);
    }

    private ValidatedCustomFieldValue number(
            CustomFieldDefinitionSnapshot definition,
            Object rawValue,
            int defaultScale,
            boolean currency
    ) {
        try {
            int scale = configuredInteger(definition, "scale", defaultScale);
            BigDecimal value = new BigDecimal(String.valueOf(rawValue)).setScale(scale, RoundingMode.HALF_UP);
            BigDecimal minimum = configuredDecimal(definition, "min");
            BigDecimal maximum = configuredDecimal(definition, "max");
            if (minimum != null && value.compareTo(minimum) < 0) {
                throw invalid(definition, "最小值不能小于 " + minimum);
            }
            if (maximum != null && value.compareTo(maximum) > 0) {
                throw invalid(definition, "最大值不能大于 " + maximum);
            }
            return currency
                    ? new ValidatedCustomFieldValue(value, null, null, null, value, null, null, null)
                    : new ValidatedCustomFieldValue(value, null, null, value, null, null, null, null);
        } catch (NumberFormatException exception) {
            throw invalid(definition, "字段值必须是数字");
        }
    }

    private ValidatedCustomFieldValue email(CustomFieldDefinitionSnapshot definition, Object rawValue) {
        String value = String.valueOf(rawValue).trim();
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw invalid(definition, "邮箱格式不正确");
        }
        return text(definition, value, DEFAULT_TEXT_MAX_LENGTH, false);
    }

    private ValidatedCustomFieldValue url(CustomFieldDefinitionSnapshot definition, Object rawValue) {
        String value = String.valueOf(rawValue).trim();
        try {
            java.net.URI uri = java.net.URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw invalid(definition, "链接必须使用 http 或 https");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid(definition, "链接格式不正确");
        }
        return text(definition, value, DEFAULT_TEXT_MAX_LENGTH, false);
    }

    private ValidatedCustomFieldValue singleSelect(CustomFieldDefinitionSnapshot definition, Object rawValue) {
        String optionCode = String.valueOf(rawValue);
        requireOption(definition, optionCode);
        return new ValidatedCustomFieldValue(optionCode, optionCode, null, null, null, null, null, null);
    }

    private ValidatedCustomFieldValue multiSelect(CustomFieldDefinitionSnapshot definition, Object rawValue) {
        if (!(rawValue instanceof Collection<?> collection)) {
            throw invalid(definition, "多选字段只能传入数组");
        }
        List<String> optionCodes = collection.stream().map(String::valueOf).distinct().toList();
        if (optionCodes.size() > MAX_MULTI_SELECT_VALUES) {
            throw invalid(definition, "最多支持 " + MAX_MULTI_SELECT_VALUES + " 项");
        }
        optionCodes.forEach(optionCode -> requireOption(definition, optionCode));
        return new ValidatedCustomFieldValue(optionCodes, null, null, null, null, null, null, null);
    }

    private ValidatedCustomFieldValue date(CustomFieldDefinitionSnapshot definition, Object rawValue) {
        try {
            LocalDate value = rawValue instanceof LocalDate localDate
                    ? localDate
                    : LocalDate.parse(String.valueOf(rawValue));
            return new ValidatedCustomFieldValue(value.toString(), null, null, null, null, value, null, null);
        } catch (DateTimeParseException exception) {
            throw invalid(definition, "日期格式应为 YYYY-MM-DD");
        }
    }

    private ValidatedCustomFieldValue datetime(CustomFieldDefinitionSnapshot definition, Object rawValue) {
        try {
            LocalDateTime value = rawValue instanceof LocalDateTime localDateTime
                    ? localDateTime
                    : LocalDateTime.parse(String.valueOf(rawValue));
            return new ValidatedCustomFieldValue(value.toString(), null, null, null, null, null, value, null);
        } catch (DateTimeParseException exception) {
            throw invalid(definition, "日期时间格式应为 yyyy-MM-ddTHH:mm:ss");
        }
    }

    private ValidatedCustomFieldValue bool(CustomFieldDefinitionSnapshot definition, Object rawValue) {
        Boolean value;
        if (rawValue instanceof Boolean bool) {
            value = bool;
        } else {
            value = switch (String.valueOf(rawValue).trim().toLowerCase()) {
                case "true", "1", "yes", "是" -> true;
                case "false", "0", "no", "否" -> false;
                default -> throw invalid(definition, "布尔值只能是 true 或 false");
            };
        }
        return new ValidatedCustomFieldValue(value, null, null, null, null, null, null, value);
    }

    private ValidatedCustomFieldValue attachment(CustomFieldDefinitionSnapshot definition, Object rawValue) {
        List<Object> attachments = normalizeAttachmentValues(rawValue);
        int maxAttachments = configuredInteger(definition, "maxFiles", DEFAULT_MAX_ATTACHMENTS);
        if (attachments.size() > maxAttachments) {
            throw invalid(definition, "图片数量不能超过 " + maxAttachments);
        }
        return new ValidatedCustomFieldValue(attachments, null, null, null, null, null, null, null);
    }

    private ValidatedCustomFieldValue fileValue(
            CustomFieldDefinitionSnapshot definition,
            Object rawValue,
            int defaultMaxFiles,
            String label
    ) {
        List<Map<String, Object>> files = normalizeFileReferences(rawValue);
        int maxFiles = Math.min(30, Math.max(1, configuredInteger(definition, "maxFiles", defaultMaxFiles)));
        if (files.size() > maxFiles) {
            throw invalid(definition, label + "数量不能超过 " + maxFiles);
        }
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("version", 1);
        value.put("files", files);
        return new ValidatedCustomFieldValue(value, null, null, null, null, null, null, null);
    }

    private List<Map<String, Object>> normalizeFileReferences(Object rawValue) {
        Object value = rawValue;
        if (value instanceof Map<?, ?> map && map.get("files") != null) {
            value = map.get("files");
        }
        Collection<?> source;
        if (value == null) {
            source = List.of();
        } else if (value instanceof Collection<?> collection) {
            source = collection;
        } else {
            source = List.of(value);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int sortOrder = 0;
        for (Object item : source) {
            LinkedHashMap<String, Object> reference = new LinkedHashMap<>();
            if (item instanceof Number number) {
                reference.put("fileId", number.longValue());
            } else if (item instanceof Map<?, ?> map) {
                Long fileId = positiveLong(map.get("fileId"));
                if (fileId == null) fileId = positiveLong(map.get("id"));
                if (fileId != null) {
                    reference.put("fileId", fileId);
                } else {
                    Object url = chooseFirst(map, "url", "path", "storagePath", "storage_path", "downloadUrl");
                    if (url != null) {
                        reference.put("url", String.valueOf(url).replace("/api/files/public/", "/files/public/"));
                        Object name = chooseFirst(map, "name", "fileName", "filename", "originalName", "original_name");
                        reference.put("name", name == null ? lastPathSegment(String.valueOf(url)) : name);
                        reference.put("legacy", true);
                    }
                }
            } else if (item != null) {
                String text = String.valueOf(item).trim();
                Long fileId = positiveLong(text);
                if (fileId != null) {
                    reference.put("fileId", fileId);
                } else if (!text.isBlank()) {
                    reference.put("url", text.replace("/api/files/public/", "/files/public/"));
                    reference.put("name", lastPathSegment(text));
                    reference.put("legacy", true);
                }
            }
            if (!reference.isEmpty()) {
                reference.put("sortOrder", sortOrder++);
                result.add(reference);
            }
        }
        return result;
    }

    private Long positiveLong(Object value) {
        if (value == null) return null;
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void requireOption(CustomFieldDefinitionSnapshot definition, String optionCode) {
        if (!definition.activeOptionCodes().contains(optionCode)) {
            throw invalid(definition, "选项不存在或已禁用");
        }
    }

    private int configuredInteger(CustomFieldDefinitionSnapshot definition, String key, int fallback) {
        Object value = definition.config().get(key);
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private BigDecimal configuredDecimal(CustomFieldDefinitionSnapshot definition, String key) {
        Object value = definition.config().get(key);
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw invalid(definition, "字段数值范围配置无效");
        }
    }

    private boolean isEmpty(Object value) {
        return value == null
                || value instanceof String text && text.isBlank()
                || value instanceof Collection<?> collection && collection.isEmpty();
    }

    private List<Object> normalizeAttachmentValues(Object rawValue) {
        Object value = rawValue;
        if (rawValue instanceof String rawText) {
            String text = rawText.trim();
            if (text.isEmpty()) return List.of();
            value = List.of(text);
        } else if (!(rawValue instanceof Collection<?> collection)) {
            value = List.of(rawValue);
        }

        Collection<?> collection = (Collection<?>) value;
        if (collection.isEmpty()) return List.of();

        List<Object> result = new ArrayList<>();
        for (Object item : collection) {
            Map<String, Object> normalized = normalizeAttachmentItem(item);
            if (normalized.isEmpty()) continue;
            result.add(normalized);
        }
        return result;
    }

    private Map<String, Object> normalizeAttachmentItem(Object rawItem) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (rawItem == null) {
            return normalized;
        }
        if (rawItem instanceof Map<?, ?> source) {
            Object id = source.get("id");
            Object name = chooseFirst(source, "name", "fileName", "filename", "originalName", "original_name");
            Object url = chooseFirst(source, "url", "path", "storagePath", "storage_path", "downloadUrl");
            if (id != null) normalized.put("id", id);
            if (name != null) normalized.put("name", String.valueOf(name));
            if (url != null) normalized.put("url", String.valueOf(url));
            return normalized;
        }

        String text = String.valueOf(rawItem).trim();
        if (text.isBlank()) {
            return normalized;
        }
        normalized.put("url", text);
        normalized.put("name", lastPathSegment(text));
        return normalized;
    }

    private Object chooseFirst(Map<?, ?> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) return text;
            }
        }
        return null;
    }

    private String lastPathSegment(String url) {
        int index = Math.max(url.lastIndexOf('/'), url.lastIndexOf('\\'));
        if (index < 0) return url;
        String suffix = url.substring(index + 1);
        return suffix.isBlank() ? url : suffix;
    }

    private BusinessException invalid(CustomFieldDefinitionSnapshot definition, String reason) {
        String prefix = definition == null ? "" : definition.fieldName() + "：";
        return new BusinessException(400, prefix + reason);
    }
}

package com.dehui.property.common;

import java.util.regex.Pattern;

public final class ContactValidators {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private ContactValidators() {
    }

    public static String optionalPhone(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(400, "联系电话格式不正确");
        }
        return normalized;
    }

    public static String requiredPhone(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new BusinessException(400, "联系电话格式不正确");
        }
        return optionalPhone(normalized);
    }

    public static String optionalEmail(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() || "null".equalsIgnoreCase(normalized) ? null : normalized;
    }
}

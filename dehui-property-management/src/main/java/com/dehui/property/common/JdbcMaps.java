package com.dehui.property.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class JdbcMaps {

    private JdbcMaps() {
    }

    public static String code(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    public static String str(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    public static String strOr(Map<String, Object> body, String fallback, String... keys) {
        String value = str(body, keys);
        return value == null ? fallback : value;
    }

    public static String requiredStr(Map<String, Object> body, String message, String... keys) {
        String value = str(body, keys);
        if (value == null) {
            throw new BusinessException(400, message);
        }
        return value;
    }

    public static Long longVal(Map<String, Object> body, String... keys) {
        Object value = first(body, keys);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }

    public static Long requiredLong(Map<String, Object> body, String message, String... keys) {
        Long value = longVal(body, keys);
        if (value == null) {
            throw new BusinessException(400, message);
        }
        return value;
    }

    public static Integer intVal(Map<String, Object> body, Integer fallback, String... keys) {
        Object value = first(body, keys);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : Integer.valueOf(text);
    }

    public static BigDecimal decimal(Map<String, Object> body, BigDecimal fallback, String... keys) {
        Object value = first(body, keys);
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : new BigDecimal(text);
    }

    public static Boolean bool(Map<String, Object> body, Boolean fallback, String... keys) {
        Object value = first(body, keys);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : Boolean.valueOf(text);
    }

    public static String date(Map<String, Object> body, String fallback, String... keys) {
        String value = str(body, keys);
        return value == null ? fallback : value.substring(0, Math.min(value.length(), 10));
    }

    public static String datetime(Map<String, Object> body, String fallback, String... keys) {
        String value = str(body, keys);
        if (value == null) {
            return fallback;
        }
        return value.replace('T', ' ');
    }

    public static String today() {
        return LocalDate.now().toString();
    }

    public static String now() {
        return LocalDateTime.now().toString().replace('T', ' ');
    }

    private static Object first(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}

package com.dehui.property.modules.inspection.application;

import com.dehui.property.common.BusinessException;

/**
 * Validates the inspector display snapshot without deriving or accepting a system identity.
 */
public final class InspectionInspectorValueResolver {

    private static final int MAX_LENGTH = 64;

    private InspectionInspectorValueResolver() {
    }

    public static String resolve(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isBlank()) {
            throw new BusinessException(400, "巡检人不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new BusinessException(400, "巡检人不能超过64个字符");
        }
        return value;
    }
}

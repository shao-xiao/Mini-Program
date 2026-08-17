package com.dehui.property.modules.inspection.application;

import com.dehui.property.common.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Resolves configured inspection types to their stable code while preserving an unchanged
 * historical value during admin edits.
 */
public final class InspectionTypeValueResolver {

    private static final int MAX_LENGTH = 32;

    private InspectionTypeValueResolver() {
    }

    public static String resolve(JdbcTemplate jdbcTemplate, String rawValue, String currentType) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isBlank()) {
            throw new BusinessException(400, "巡检类型不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw new BusinessException(400, "巡检类型不能超过32个字符");
        }
        if (currentType != null && value.equalsIgnoreCase(currentType.trim())) {
            return currentType.trim();
        }

        List<Map<String, Object>> matches = jdbcTemplate.queryForList(
                """
                SELECT code, name, status
                FROM inspection_type
                WHERE deleted = 0
                  AND (
                    UPPER(TRIM(code)) = UPPER(TRIM(?))
                    OR UPPER(TRIM(name)) = UPPER(TRIM(?))
                  )
                ORDER BY CASE WHEN UPPER(TRIM(code)) = UPPER(TRIM(?)) THEN 0 ELSE 1 END, id ASC
                LIMIT 1
                """,
                value,
                value,
                value
        );
        if (matches.isEmpty()) {
            throw new BusinessException(400, "巡检类型无效，请从启用类型中选择");
        }

        Map<String, Object> match = matches.get(0);
        String code = String.valueOf(match.getOrDefault("code", "")).trim();
        String status = String.valueOf(match.getOrDefault("status", "")).trim();
        if (code.isBlank() || code.length() > MAX_LENGTH) {
            throw new BusinessException(400, "巡检类型配置无效，请联系管理员");
        }
        if ("ENABLED".equalsIgnoreCase(status)) {
            return code;
        }
        throw new BusinessException(400, "巡检类型已停用，不能使用");
    }
}

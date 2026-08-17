package com.dehui.property.modules.system.dto;

import java.util.List;
import java.util.Map;

public record UserAccessResponse(
        List<String> roles,
        List<String> permissions,
        List<Map<String, Object>> menus
) {
}

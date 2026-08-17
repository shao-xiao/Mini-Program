package com.dehui.property.modules.system.dto;

import java.util.List;
import java.util.Map;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String realName,
        List<String> roles,
        List<String> permissions,
        List<Map<String, Object>> menus,
        Map<String, Object> userInfo
) {
}

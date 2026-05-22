package com.dehui.property.security;

import java.util.List;

public record AuthPrincipal(
        Long userId,
        String username,
        String userType,
        Long tenantId,
        List<String> roles,
        List<String> permissions
) {
}

package com.dehui.property.modules.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.system.dto.UserAccessResponse;
import com.dehui.property.security.AuthPrincipal;
import com.dehui.property.security.TokenService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class SystemAuthServiceTest {

    @Test
    void superAdminReceivesEveryEnabledPermissionInsteadOfAStaleRoleSnapshot() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("SELECT r.code")),
                eq(String.class),
                eq(1L)
        )).thenReturn(List.of("ADMIN", "SUPER_ADMIN"));
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null
                        && sql.contains("FROM sys_permission")
                        && sql.contains("ORDER BY id")),
                eq(String.class)
        )).thenReturn(List.of("bill:view", "bill:update"));
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM sys_menu")),
                any(Object[].class)
        )).thenReturn(List.of());

        SystemAuthService service = new SystemAuthService(
                jdbcTemplate,
                mock(PasswordEncoder.class),
                mock(TokenService.class)
        );
        UserAccessResponse access = service.currentAccess(new AuthPrincipal(
                1L,
                "admin",
                "SYSTEM",
                null,
                List.of("ADMIN"),
                List.of()
        ));

        assertEquals(List.of("ADMIN", "SUPER_ADMIN"), access.roles());
        assertTrue(access.permissions().containsAll(List.of("bill:view", "bill:update")));
    }
}

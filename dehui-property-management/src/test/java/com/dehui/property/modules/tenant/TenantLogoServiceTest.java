package com.dehui.property.modules.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.tenant.application.TenantLogoService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantLogoServiceTest {

    @Test
    void claimsOwnedTenantImageDraftAndReturnsProtectedAdminUrl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", 44L);
        draft.put("bizType", "CUSTOM_FIELD_IMAGE");
        draft.put("bizId", null);
        draft.put("workspaceKey", "tenants");
        draft.put("accessScope", "INTERNAL");
        draft.put("ownerSysUserId", 5L);
        draft.put("originalName", "tenant-logo.webp");
        draft.put("mimeType", "image/webp");
        Map<String, Object> tenant = new LinkedHashMap<>();
        tenant.put("logoFileId", null);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(tenant), List.of(draft));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = new TenantLogoService(jdbcTemplate).updateLogo(8L, 44L, 5L);

        assertEquals(44L, result.get("logoFileId"));
        assertEquals("tenant-logo.webp", result.get("logoFileName"));
        assertEquals("/api/files/44", result.get("logoUrl"));
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("SET biz_type = 'TENANT_LOGO'")),
                eq(8L),
                eq(5L),
                eq(44L),
                eq(5L)
        );
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("SET logo_file_id = ?")),
                eq(44L),
                eq(5L),
                eq(8L)
        );
    }

    @Test
    void rejectsLogoAlreadyBoundToAnotherTenant() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(
                        List.of(Map.of("logoFileId", 41L)),
                        List.of(Map.of(
                                "id", 44L,
                                "bizType", "TENANT_LOGO",
                                "bizId", 9L,
                                "workspaceKey", "tenants",
                                "accessScope", "INTERNAL",
                                "ownerSysUserId", 5L,
                                "originalName", "other.png",
                                "mimeType", "image/png"
                        ))
                );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new TenantLogoService(jdbcTemplate).updateLogo(8L, 44L, 5L)
        );

        assertEquals(400, exception.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void clearingLogoUnlinksTenantAndDisablesPreviousFile() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("logoFileId", 41L)));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = new TenantLogoService(jdbcTemplate).updateLogo(8L, null, 5L);

        assertEquals(null, result.get("logoFileId"));
        assertEquals("", result.get("logoUrl"));
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("SET logo_file_id = ?")),
                eq(null),
                eq(5L),
                eq(8L)
        );
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("SET status = 'DISABLED'")),
                eq(5L),
                eq(41L),
                eq(8L)
        );
    }
}

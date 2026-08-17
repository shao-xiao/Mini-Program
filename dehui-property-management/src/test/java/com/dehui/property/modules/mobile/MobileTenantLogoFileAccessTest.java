package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.config.FileStorageProperties;
import com.dehui.property.modules.mobile.service.MobileStaffFileService;
import com.dehui.property.security.AuthPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class MobileTenantLogoFileAccessTest {

    @TempDir
    Path uploadRoot;

    @Test
    void currentTenantCanDownloadItsConfiguredLogo() throws Exception {
        Path logo = uploadRoot.resolve("private/custom-fields/logo.png");
        Files.createDirectories(logo.getParent());
        Files.write(logo, new byte[] {1, 2, 3});
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(fileRow(8L)));
        when(jdbcTemplate.queryForObject(contains("FROM tenant"), eq(Long.class), eq(8L), eq(44L)))
                .thenReturn(1L);

        MobileStaffFileService.FileDownload download = new MobileStaffFileService(
                jdbcTemplate,
                new FileStorageProperties(uploadRoot.toString())
        ).requireDownload(tenantPrincipal(8L), 44L);

        assertEquals("image/png", download.mimeType());
        assertTrue(download.resource().exists());
    }

    @Test
    void anotherTenantCannotDownloadLogoEvenWithFileId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(fileRow(8L)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new MobileStaffFileService(
                        jdbcTemplate,
                        new FileStorageProperties(uploadRoot.toString())
                ).requireDownload(tenantPrincipal(9L), 44L)
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void publicIdentityCannotDownloadTenantLogo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(fileRow(8L)));
        AuthPrincipal publicPrincipal = new AuthPrincipal(
                7L,
                "mobile:7",
                "PUBLIC",
                null,
                List.of("MOBILE_USER"),
                List.of()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new MobileStaffFileService(
                        jdbcTemplate,
                        new FileStorageProperties(uploadRoot.toString())
                ).requireDownload(publicPrincipal, 44L)
        );

        assertEquals(404, exception.getCode());
    }

    private Map<String, Object> fileRow(Long tenantId) {
        return Map.of(
                "id", 44L,
                "bizType", "TENANT_LOGO",
                "bizId", tenantId,
                "workspaceKey", "tenants",
                "storagePath", "private/custom-fields/logo.png",
                "accessScope", "INTERNAL",
                "ownerSysUserId", 5L,
                "originalName", "logo.png",
                "mimeType", "image/png"
        );
    }

    private AuthPrincipal tenantPrincipal(Long tenantId) {
        return new AuthPrincipal(
                7L,
                "mobile:7",
                "TENANT",
                tenantId,
                List.of("MOBILE_USER"),
                List.of()
        );
    }
}

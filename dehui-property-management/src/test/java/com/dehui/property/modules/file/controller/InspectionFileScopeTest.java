package com.dehui.property.modules.file.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dehui.property.common.BusinessException;
import com.dehui.property.config.FileStorageProperties;
import com.dehui.property.modules.customfield.application.CustomFieldAuthorizationService;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.mobile.service.MobileStaffFileService;
import com.dehui.property.security.AuthPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class InspectionFileScopeTest {
    @TempDir Path uploadRoot;

    @Test void savedPhotoIsReadableByAnotherInspectionViewer() throws Exception {
        assertEquals(200, controller(1L, "inspections").download(116L,
                principal(25L, List.of("inspection:view"))).getStatusCode().value());
    }

    @Test void savedPhotoHonorsExistingWildcardPermissions() throws Exception {
        for (String permission : List.of("inspection:*", "*")) {
            assertEquals(200, controller(1L, "inspections").download(116L,
                    principal(25L, List.of(permission))).getStatusCode().value());
        }
    }

    @Test void savedPhotoIsHiddenWithoutInspectionPermission() throws Exception {
        FileController controller = controller(1L, "inspections");
        assertEquals(404, assertThrows(BusinessException.class, () -> controller.download(116L,
                principal(25L, List.of("workorder:view")))).getCode());
    }

    @Test void unsavedOrDeletedAttachmentRemainsPrivate() throws Exception {
        FileController controller = controller(0L, "inspections");
        assertEquals(404, assertThrows(BusinessException.class, () -> controller.download(116L,
                principal(25L, List.of("inspection:view")))).getCode());
    }

    @Test void uploaderCanStillReadTheirDraft() throws Exception {
        assertEquals(200, controller(0L, "inspections").download(116L,
                principal(16L, List.of("inspection:view"))).getStatusCode().value());
    }

    @Test void anotherWorkspaceDoesNotGainInspectionAccess() throws Exception {
        FileController controller = controller(1L, "workorders");
        assertEquals(404, assertThrows(BusinessException.class, () -> controller.download(116L,
                principal(25L, List.of("inspection:view")))).getCode());
    }

    private AuthPrincipal principal(Long id, List<String> permissions) {
        return new AuthPrincipal(id, "viewer", "SYSTEM", null, List.of("ENGINEER_MANAGER"), permissions);
    }

    private FileController controller(long linkedCount, String workspace) throws Exception {
        Files.writeString(uploadRoot.resolve("photo.png"), "photo");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 116L);
        row.put("bizType", "CUSTOM_FIELD_IMAGE");
        row.put("workspaceKey", workspace);
        row.put("ownerSysUserId", 16L);
        row.put("storagePath", "photo.png");
        row.put("accessScope", "INTERNAL");
        row.put("originalName", "photo.png");
        row.put("mimeType", "image/png");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM file_upload"), any(Object[].class))).thenReturn(List.of(row));
        when(jdbc.queryForObject(contains("FROM inspection_attachment"), eq(Long.class), eq(116L)))
                .thenReturn(linkedCount);
        FileStorageProperties properties = new FileStorageProperties(uploadRoot.toString());
        FileController controller = new FileController(jdbc, properties,
                new CustomFieldWorkspaceRegistry(), new CustomFieldAuthorizationService());
        controller.setMobileStaffFileService(new MobileStaffFileService(jdbc, properties));
        return controller;
    }
}

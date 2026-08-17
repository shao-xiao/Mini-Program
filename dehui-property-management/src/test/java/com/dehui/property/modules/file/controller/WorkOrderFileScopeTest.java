package com.dehui.property.modules.file.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.config.FileStorageProperties;
import com.dehui.property.modules.customfield.application.CustomFieldAuthorizationService;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.mobile.service.MobileStaffFileService;
import com.dehui.property.security.AuthPrincipal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkOrderFileScopeTest {

    private static final AuthPrincipal LIMITED_VIEWER = new AuthPrincipal(
            35L,
            "readonly",
            "SYSTEM",
            null,
            List.of("READ_ONLY"),
            List.of("workorder:view")
    );
    private static final AuthPrincipal GLOBAL_VIEWER = new AuthPrincipal(
            1L,
            "admin",
            "SYSTEM",
            null,
            List.of("ADMIN"),
            List.of("workorder:view", "workorder:assign")
    );
    private static final AuthPrincipal OTHER_LIMITED_VIEWER = new AuthPrincipal(
            36L,
            "other-readonly",
            "SYSTEM",
            null,
            List.of("READ_ONLY"),
            List.of("workorder:view")
    );
    private static final AuthPrincipal SUPER_ADMIN = new AuthPrincipal(
            1L,
            "super-admin",
            "SYSTEM",
            null,
            List.of("SUPER_ADMIN"),
            List.of()
    );
    private static final AuthPrincipal MODULE_WILDCARD_VIEWER = new AuthPrincipal(
            2L,
            "module-wildcard",
            "SYSTEM",
            null,
            List.of("BUSINESS"),
            List.of("workorder:*")
    );
    private static final AuthPrincipal GLOBAL_WILDCARD_VIEWER = new AuthPrincipal(
            3L,
            "global-wildcard",
            "SYSTEM",
            null,
            List.of("BUSINESS"),
            List.of("*")
    );

    @TempDir
    Path uploadRoot;

    @Test
    void fileEndpointHidesAnotherAssigneesWorkOrderFileFromALimitedViewer() throws IOException {
        JdbcTemplate jdbcTemplate = fileJdbcTemplate(0L);
        FileController controller = fileController(jdbcTemplate);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.download(36L, LIMITED_VIEWER)
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void fileEndpointRemainsReadableForAViewerWithGlobalAssignmentScope() throws IOException {
        JdbcTemplate jdbcTemplate = fileJdbcTemplate(0L);
        FileController controller = fileController(jdbcTemplate);

        ResponseEntity<Resource> response = controller.download(36L, GLOBAL_VIEWER);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("work-order-12.png", response.getBody().getFilename());
        verify(jdbcTemplate, never()).queryForObject(
                contains("FROM work_order"),
                eq(Long.class),
                any(Object[].class)
        );
    }

    @Test
    void fileEndpointRemainsReadableForTheAssignedLimitedViewer() throws IOException {
        JdbcTemplate jdbcTemplate = fileJdbcTemplate(1L);
        FileController controller = fileController(jdbcTemplate);

        ResponseEntity<Resource> response = controller.download(36L, LIMITED_VIEWER);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void fileEndpointHonorsSuperAdminAndWildcardGlobalScopes() throws IOException {
        for (AuthPrincipal principal : List.of(
                SUPER_ADMIN,
                MODULE_WILDCARD_VIEWER,
                GLOBAL_WILDCARD_VIEWER
        )) {
            JdbcTemplate jdbcTemplate = fileJdbcTemplate(0L);
            ResponseEntity<Resource> response = fileController(jdbcTemplate).download(36L, principal);
            assertEquals(200, response.getStatusCode().value());
            verify(jdbcTemplate, never()).queryForObject(
                    contains("FROM work_order"),
                    eq(Long.class),
                    any(Object[].class)
            );
        }
    }

    @Test
    void workOrderCustomFieldDraftRemainsReadableForItsUploader() throws IOException {
        JdbcTemplate jdbcTemplate = fileJdbcTemplate(customFieldDraftRow(35L), 0L);
        FileController controller = fileController(jdbcTemplate);

        ResponseEntity<Resource> response = controller.download(36L, LIMITED_VIEWER);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void workOrderCustomFieldDraftIsHiddenFromAnotherViewer() throws IOException {
        JdbcTemplate jdbcTemplate = fileJdbcTemplate(customFieldDraftRow(35L), 0L);
        FileController controller = fileController(jdbcTemplate);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.download(36L, OTHER_LIMITED_VIEWER)
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void savedWorkOrderCustomFieldFileUsesTheAssignedWorkOrderScope() throws IOException {
        JdbcTemplate jdbcTemplate = fileJdbcTemplate(savedCustomFieldRow(), 1L);

        ResponseEntity<Resource> response = fileController(jdbcTemplate).download(36L, LIMITED_VIEWER);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void savedWorkOrderCustomFieldFileIsHiddenFromAnotherLimitedViewer() throws IOException {
        JdbcTemplate jdbcTemplate = fileJdbcTemplate(savedCustomFieldRow(), 0L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fileController(jdbcTemplate).download(36L, OTHER_LIMITED_VIEWER)
        );

        assertEquals(404, exception.getCode());
    }

    private JdbcTemplate fileJdbcTemplate(long assignedCount) {
        return fileJdbcTemplate(workOrderFileRow(), assignedCount);
    }

    private JdbcTemplate fileJdbcTemplate(Map<String, Object> fileRow, long assignedCount) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FROM file_upload"), any(Object[].class)))
                .thenReturn(List.of(fileRow));
        when(jdbcTemplate.queryForObject(contains("FROM work_order"), eq(Long.class), any(Object[].class)))
                .thenReturn(assignedCount);
        return jdbcTemplate;
    }

    private FileController fileController(JdbcTemplate jdbcTemplate) throws IOException {
        Path target = uploadRoot.resolve("private/mobile/work-order-12.png");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "test");

        FileStorageProperties properties = new FileStorageProperties(uploadRoot.toString());
        FileController controller = new FileController(
                jdbcTemplate,
                properties,
                new CustomFieldWorkspaceRegistry(),
                new CustomFieldAuthorizationService()
        );
        controller.setMobileStaffFileService(new MobileStaffFileService(jdbcTemplate, properties));
        return controller;
    }

    private Map<String, Object> workOrderFileRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 36L);
        row.put("bizType", "WORK_ORDER");
        row.put("bizId", 12L);
        row.put("workspaceKey", "workorders");
        row.put("storagePath", "private/mobile/work-order-12.png");
        row.put("accessScope", "INTERNAL");
        row.put("ownerSysUserId", 30L);
        row.put("originalName", "work-order-12.png");
        row.put("mimeType", "image/png");
        return row;
    }

    private Map<String, Object> customFieldDraftRow(Long ownerSysUserId) {
        Map<String, Object> row = workOrderFileRow();
        row.put("bizType", "CUSTOM_FIELD_IMAGE");
        row.put("bizId", null);
        row.put("ownerSysUserId", ownerSysUserId);
        return row;
    }

    private Map<String, Object> savedCustomFieldRow() {
        Map<String, Object> row = workOrderFileRow();
        row.put("bizType", "CUSTOM_FIELD");
        row.put("bizId", 12L);
        row.put("workspaceKey", "workorders");
        return row;
    }
}

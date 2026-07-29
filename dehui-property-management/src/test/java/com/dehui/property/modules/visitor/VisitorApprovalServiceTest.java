package com.dehui.property.modules.visitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.visitor.application.VisitorApprovalService;
import com.dehui.property.security.AuthPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class VisitorApprovalServiceTest {

    private JdbcTemplate jdbcTemplate;
    private VisitorApprovalService service;
    private AuthPrincipal principal;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new VisitorApprovalService(jdbcTemplate);
        principal = new AuthPrincipal(
                88L,
                "visitor-reviewer",
                "SYSTEM",
                null,
                List.of("PROPERTY_MANAGER"),
                List.of("visitor:view", "visitor:update")
        );
    }

    @Test
    void approvesOnlyPendingAppointmentsAndRecordsReviewer() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 9L, "status", "PENDING")
        ));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.approve(9L, "资料已确认", principal);

        assertEquals("REGISTERED", result.get("status"));
        assertEquals("资料已确认", result.get("reviewRemark"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("reviewed_by"));
        assertTrue(sql.getValue().contains("appointment_status = 'PENDING'"));
    }

    @Test
    void rejectedAppointmentRequiresAReason() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 10L, "status", "PENDING")
        ));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.reject(10L, "   ", principal)
        );

        assertEquals(400, error.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void attachesOwnedVisitorImageAfterApprovalAndDisablesReplacedFile() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", 11L,
                        "status", "REGISTERED",
                        "qrCodeFileId", 21L
                )))
                .thenReturn(List.of(Map.of(
                        "id", 22L,
                        "originalName", "access-qr.png",
                        "mimeType", "image/png",
                        "fileSize", 2048L
                )));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.attachQrCode(11L, 22L, principal);

        assertEquals(22L, result.get("qrCodeFileId"));
        assertEquals("/api/files/22", result.get("url"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(3)).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getAllValues().stream().anyMatch(value -> value.contains("biz_type = 'VISITOR'")));
        assertTrue(sql.getAllValues().stream().anyMatch(value -> value.contains("status = 'DISABLED'")));
    }

    @Test
    void blocksQrAttachmentBeforeReview() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 12L, "status", "PENDING")
        ));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.attachQrCode(12L, 23L, principal)
        );

        assertEquals(409, error.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void mobileStaffClaimsItsInternalDraftAndUsesSystemStaffIdentity() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", 13L,
                        "status", "REGISTERED"
                )))
                .thenReturn(List.of(Map.of(
                        "id", 24L,
                        "originalName", "mobile-access-qr.png",
                        "mimeType", "image/png",
                        "fileSize", 4096L
                )));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.attachStaffQrCode(13L, 24L, 109L);

        assertEquals(24L, result.get("qrCodeFileId"));
        assertEquals("/api/mobile/files/24", result.get("url"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getAllValues().stream().anyMatch(value -> value.contains("biz_type = 'STAFF_DRAFT'")));
        assertTrue(sql.getAllValues().stream().anyMatch(value -> value.contains("workspace_key = 'visitors'")));
    }

    @Test
    void mobileStaffApprovalRejectsASecondStateTransition() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 14L, "status", "REGISTERED")
        ));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.approveByStaff(14L, null, 109L)
        );

        assertEquals(409, error.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}

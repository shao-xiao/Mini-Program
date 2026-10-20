package com.dehui.property.modules.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.inspection.controller.InspectionController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import com.dehui.property.security.AuthPrincipal;
import org.springframework.jdbc.core.RowMapper;

class InspectionControllerTest {

    @Test
    void rejectsNewTypeCodesThatCannotFitInspectionRecords() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> new InspectionController(jdbcTemplate).createInspectionType(
                        new AuthPrincipal(1L, "admin", "ADMIN", null, List.of("ADMIN"), List.of("inspection:update")),
                        Map.of(
                                "code", "X".repeat(33),
                                "name", "超长编码类型",
                                "status", "ENABLED",
                                "sortOrder", 1
                        )
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void rejectsUpdatingAnOverlongExistingTypeToEnabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        String overlongCode = "X".repeat(33);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("code", overlongCode)));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> new InspectionController(jdbcTemplate).updateInspectionType(
                        7L,
                        new AuthPrincipal(1L, "admin", "ADMIN", null, List.of("ADMIN"), List.of("inspection:update")),
                        Map.of(
                                "code", overlongCode,
                                "name", "历史超长编码类型",
                                "status", "ENABLED",
                                "sortOrder", 1
                        )
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void rejectsEnablingAnOverlongExistingType() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("code", "X".repeat(33))));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> new InspectionController(jdbcTemplate).enableInspectionType(
                        7L,
                        new AuthPrincipal(1L, "admin", "ADMIN", null, List.of("ADMIN"), List.of("inspection:update"))
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void detailSeparatesOriginalAttachmentsFromPropertyHistoryAttachments() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", 39L,
                        "code", "INSP-39",
                        "inspectionType", "STRONG_ELECTRIC",
                        "status", "OPEN"
                )))
                .thenReturn(List.of(Map.of(
                        "id", 10L,
                        "fileId", 81L,
                        "name", "巡检现场.jpg",
                        "url", "/api/files/81",
                        "sortOrder", 1
                )))
                .thenReturn(List.of(Map.of(
                        "id", 90L,
                        "action", "UPDATE",
                        "content", "已完成复查",
                        "operatorName", "张昭",
                        "operatedAt", "2026-07-30 18:38:51"
                )))
                .thenReturn(List.of(Map.of(
                        "historyId", 90L,
                        "id", 82L,
                        "name", "处理后.jpg",
                        "url", "/api/files/82"
                )));

        ApiResponse<Map<String, Object>> response = new InspectionController(jdbcTemplate).inspection(39L);

        assertEquals(200, response.code());
        assertEquals(1, ((List<?>) response.data().get("attachments")).size());
        List<?> history = (List<?>) response.data().get("history");
        assertEquals(1, history.size());
        Map<?, ?> historyItem = (Map<?, ?>) history.getFirst();
        assertFalse(((List<?>) historyItem.get("resolutionImages")).isEmpty());
    }

    @Test
    void listSupportsFiltersAndPagination() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 1L, "target", "消防栓", "status", "OPEN")
        ));

        ApiResponse<PageResponse<Map<String, Object>>> response = new InspectionController(jdbcTemplate)
                .inspections("张三", "消防", "FIRE", "ABNORMAL", "OPEN", "2026-05-01", "2026-05-31",
                        "updatedAt", "ascending", "inspectionType:asc", "inspectionType:asc,updatedAt:desc", 2, 10);

        assertEquals(200, response.code());
        assertEquals(7L, response.data().total());
        assertEquals(2, response.data().page());
        assertEquals(10, response.data().pageSize());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("i.inspector LIKE ?"));
        assertTrue(sql.contains("(i.target LIKE ? OR i.area LIKE ?)"));
        assertTrue(sql.contains("i.inspection_type = ?"));
        assertTrue(sql.contains("i.result = ?"));
        assertTrue(sql.contains("i.inspection_status = ?"));
        assertTrue(sql.contains("i.inspection_date >= ?"));
        assertTrue(sql.contains("i.inspection_date <= ?"));
        assertTrue(sql.contains("ORDER BY i.inspection_type ASC, i.updated_at DESC, i.id DESC"));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
    }

    @Test
    void summaryUsesTheSameFiltersAsTheList() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "totalCount", 5L,
                "normalCount", 3L,
                "abnormalCount", 2L,
                "openCount", 1L,
                "closedCount", 4L
        ));

        ApiResponse<Map<String, Object>> response = new InspectionController(jdbcTemplate)
                .summary("张三", "消防", "FIRE", "ABNORMAL", "OPEN", "2026-05-01", "2026-05-31");

        assertEquals(200, response.code());
        assertEquals(5L, response.data().get("totalCount"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("COUNT(*) AS totalCount"));
        assertTrue(sql.contains("i.inspector LIKE ?"));
        assertTrue(sql.contains("i.inspection_type = ?"));
        assertTrue(sql.contains("i.result = ?"));
        assertTrue(sql.contains("i.inspection_status = ?"));
    }

    @Test
    void updateChangesEditableInspectionFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(java.util.List.of(Map.of("inspection_type", "DAILY")));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = new InspectionController(jdbcTemplate)
                .update(15L, Map.of(
                        "inspectionDate", "2026-06-01",
                        "inspector", "测试",
                        "area", "1F",
                        "target", "消防栓",
                        "result", "NORMAL",
                        "problemDescription", "",
                        "actionTaken", "已检查",
                        "status", "OPEN",
                        "remark", "复核"
                ), new AuthPrincipal(1L, "admin", "ADMIN", null, java.util.List.of("ADMIN"), java.util.List.of("inspection:update")));

        assertEquals(200, response.code());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("UPDATE inspection_record SET"));
        assertTrue(sql.contains("inspection_date = ?"));
        assertTrue(sql.contains("inspection_status = ?"));
        assertTrue(sql.contains("WHERE id = ? AND deleted = 0"));
    }

    @Test
    void updatePreservesOrRestoresExistingPhotosWhenMysqlReturnsBooleanDeletionFlags() {
        for (Object deleted : List.of(false, true, 0, 1, "0", "1")) {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(Map.of("inspection_type", "DAILY", "inspection_status", "OPEN")))
                    .thenReturn(List.of(Map.of("mimeType", "image/jpeg")))
                    .thenReturn(List.of(Map.of("id", 84L, "fileId", 116L, "deleted", deleted)));
            when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
            ApiResponse<Void> response = new InspectionController(jdbc).update(127L, Map.of(
                    "inspectionDate", "2026-10-13", "inspector", "陈齐", "area", "B2至F8", "target", "公共设施",
                    "remark", "已解决", "images", List.of(Map.of("fileId", 116L, "sortOrder", 1))
            ), new AuthPrincipal(1L, "admin", "ADMIN", null, List.of("ADMIN"), List.of("inspection:update")));
            assertEquals(200, response.code());
            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(jdbc, org.mockito.Mockito.times(3)).update(sql.capture(), any(Object[].class));
            assertFalse(sql.getAllValues().stream().anyMatch(value -> value.contains("INSERT INTO inspection_attachment")));
            boolean wasDeleted = Boolean.TRUE.equals(deleted) || "1".equals(String.valueOf(deleted));
            assertTrue(sql.getAllValues().get(1).contains(wasDeleted ? "WHERE id = ?" : "WHERE inspection_id = ? AND file_id = ? AND deleted = 0"));
        }
    }

    @Test
    void updateStillRejectsMalformedAttachmentIds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("inspection_type", "DAILY")));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        BusinessException error = assertThrows(BusinessException.class, () -> new InspectionController(jdbc).update(127L, Map.of(
                "inspectionDate", "2026-10-13", "inspector", "陈齐", "area", "B2", "target", "公共设施",
                "images", List.of(Map.of("fileId", "invalid-file"))
        ), new AuthPrincipal(1L, "admin", "ADMIN", null, List.of("ADMIN"), List.of("inspection:update"))));
        assertEquals(400, error.getCode());
    }

    @Test
    void deleteSoftDeletesInspectionRecord() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = new InspectionController(jdbcTemplate).delete(
                15L,
                new AuthPrincipal(1L, "admin", "ADMIN", null, java.util.List.of("ADMIN"), java.util.List.of("inspection:delete"))
        );

        assertEquals(200, response.code());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getAllValues().stream().anyMatch(sql ->
                sql.contains("UPDATE inspection_record SET deleted = 1")
                        && sql.contains("WHERE id = ? AND deleted = 0")
        ));
    }
}

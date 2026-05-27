package com.dehui.property.modules.investment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.investment.controller.InvestmentController;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;

class InvestmentControllerTest {

    @Test
    void adminContentListSelectsMiniProgramSectionFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(row(
                "id", 1L,
                "code", "INV001",
                "sectionKey", "hero",
                "sectionName", "顶部主视觉",
                "title", "招商中心",
                "subtitle", "优质办公空间",
                "content", "近地铁 / 配套完善",
                "imageUrl", "",
                "publishStatus", "PUBLISHED",
                "sortOrder", 10,
                "status", "ACTIVE",
                "updatedAt", "2026-05-26 10:00:00"
        )));

        ApiResponse<PageResponse<Map<String, Object>>> response = new InvestmentController(jdbcTemplate)
                .displays(1, 20);

        Map<String, Object> record = response.data().records().getFirst();
        assertEquals("hero", record.get("sectionKey"));
        assertEquals("顶部主视觉", record.get("sectionName"));
        assertEquals("优质办公空间", record.get("subtitle"));
        assertEquals("", record.get("imageUrl"));
        assertEquals("2026-05-26 10:00:00", record.get("updatedAt"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(20), eq(0));
        assertTrue(sql.getValue().contains("section_key AS sectionKey"));
        assertTrue(sql.getValue().contains("subtitle"));
        assertTrue(sql.getValue().contains("image_url AS imageUrl"));
        assertTrue(sql.getValue().contains("updated_at AS updatedAt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileOverviewReturnsPublishedContentGroupedBySection() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                row(
                        "sectionKey", "hero",
                        "title", "招商中心",
                        "subtitle", "德汇创新中心",
                        "content", "近地铁 / 配套完善",
                        "imageUrl", "",
                        "sortOrder", 10
                ),
                row(
                        "sectionKey", "highlight",
                        "title", "近地铁交通便利",
                        "subtitle", "",
                        "content", "交通便利，周边配套完善",
                        "imageUrl", "",
                        "sortOrder", 20
                )
        ));

        ApiResponse<Map<String, Object>> response = new InvestmentController(jdbcTemplate).mobileOverview();

        Map<String, Object> data = response.data();
        assertTrue(data.containsKey("hero"));
        assertTrue(data.containsKey("highlight"));
        assertTrue(data.containsKey("policy"));
        assertTrue(data.containsKey("introduction"));
        assertTrue(data.containsKey("location"));
        assertTrue(data.containsKey("contact"));
        assertTrue(data.containsKey("notice"));
        assertFalse(data.containsKey("contents"));

        List<Map<String, Object>> hero = (List<Map<String, Object>>) data.get("hero");
        List<Map<String, Object>> highlight = (List<Map<String, Object>>) data.get("highlight");
        assertEquals("招商中心", hero.getFirst().get("title"));
        assertEquals("近地铁交通便利", highlight.getFirst().get("title"));
        assertTrue(((List<Map<String, Object>>) data.get("notice")).isEmpty());

        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("publish_status = 'PUBLISHED'")
                && sql.contains("section_key AS sectionKey")
                && sql.contains("ORDER BY section_key ASC, sort_order ASC")));
    }

    @Test
    void disableContentMarksContentDisabled() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(
                "UPDATE investment_display SET publish_status = 'DISABLED', updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                8L
        )).thenReturn(1);

        InvestmentController controller = new InvestmentController(jdbcTemplate);
        Method method = InvestmentController.class.getDeclaredMethod("disableContent", Long.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        method.invoke(controller, 8L);

        assertArrayEquals(new String[] {"/investment/contents/{id}/disable"}, mapping.value());
        verify(jdbcTemplate).update(
                "UPDATE investment_display SET publish_status = 'DISABLED', updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                8L
        );
    }

    @Test
    void deleteContentSoftDeletesContent() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(
                "UPDATE investment_display SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                9L
        )).thenReturn(1);

        InvestmentController controller = new InvestmentController(jdbcTemplate);
        Method method = InvestmentController.class.getDeclaredMethod("deleteContent", Long.class);
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);

        method.invoke(controller, 9L);

        assertArrayEquals(new String[] {"/investment/contents/{id}"}, mapping.value());
        verify(jdbcTemplate).update(
                "UPDATE investment_display SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                9L
        );
    }

    private static Map<String, Object> row(Object... entries) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            row.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return row;
    }
}

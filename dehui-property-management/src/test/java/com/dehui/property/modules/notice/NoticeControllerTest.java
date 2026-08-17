package com.dehui.property.modules.notice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.notice.controller.NoticeController;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;

class NoticeControllerTest {

    private JdbcTemplate jdbcTemplate;
    private NoticeController controller;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        controller = new NoticeController(jdbcTemplate);
    }

    @Test
    void deleteAnnouncementSoftDeletesNotice() throws Exception {
        when(jdbcTemplate.update(
                "UPDATE notice SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                12L
        )).thenReturn(1);

        Method method = NoticeController.class.getDeclaredMethod("delete", Long.class);
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);

        @SuppressWarnings("unchecked")
        ApiResponse<Void> response = (ApiResponse<Void>) method.invoke(controller, 12L);

        assertArrayEquals(new String[] {"/announcements/{id}"}, mapping.value());
        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                "UPDATE notice SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                12L
        );
    }

    @Test
    void mobileAnnouncementsCanBeFilteredToHomeCarouselOnly() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of(
                "id", 11L,
                "title", "首页轮播公告",
                "showOnHome", 1
        )));

        ApiResponse<List<Map<String, Object>>> response = controller.mobileNotices(true);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture());
        assertEquals(200, response.code());
        assertTrue(sql.getValue().contains("show_on_home AS showOnHome"));
        assertTrue(sql.getValue().contains("AND show_on_home = 1"));
        assertTrue(sql.getValue().contains(" ORDER BY "));
        assertFalse(sql.getValue().contains("1ORDER BY"));
    }

    @Test
    void draftAnnouncementMayKeepCarouselConfigurationButIsNotPubliclyReturned() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = controller.create(Map.of(
                "title", "端午节园区服务安排",
                "content", "服务中心正常值班",
                "type", "NOTICE",
                "status", "DRAFT",
                "showOnHome", true
        ));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertEquals(200, response.code());
        assertTrue(sql.getValue().contains("show_on_home"));
        assertEquals(1, args.getValue()[7]);
    }

    @Test
    void updateAnnouncementPersistsHomeCarouselFlag() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = controller.update(7L, Map.of(
                "title", "普通公告",
                "content", "只在公告列表展示",
                "type", "NOTICE",
                "status", "PUBLISHED",
                "showOnHome", false
        ));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertEquals(200, response.code());
        assertTrue(sql.getValue().contains("show_on_home = ?"));
        assertEquals(0, args.getValue()[6]);
    }

    @Test
    void publishedAnnouncementCanEnterHomeCarousel() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        controller.update(7L, Map.of(
                "title", "招商公告",
                "content", "欢迎预约看房",
                "type", "INVESTMENT",
                "status", "PUBLISHED",
                "showOnHome", true
        ));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), args.capture());
        assertEquals(1, args.getValue()[6]);
    }

    @Test
    void disableAnnouncementRemovesItFromHomeCarousel() {
        when(jdbcTemplate.update(
                "UPDATE notice SET publish_status = 'DISABLED', show_on_home = 0, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                15L
        )).thenReturn(1);

        ApiResponse<Void> response = controller.disable(15L);

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                "UPDATE notice SET publish_status = 'DISABLED', show_on_home = 0, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                15L
        );
    }
}

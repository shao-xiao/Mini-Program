package com.dehui.property.modules.notice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.notice.controller.NoticeController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;

class NoticeControllerTest {

    @Test
    void deleteAnnouncementSoftDeletesNotice() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(
                "UPDATE notice SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                12L
        )).thenReturn(1);

        NoticeController controller = new NoticeController(jdbcTemplate);
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
}

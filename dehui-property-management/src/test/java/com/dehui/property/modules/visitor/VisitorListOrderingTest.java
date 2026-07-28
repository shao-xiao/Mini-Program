package com.dehui.property.modules.visitor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.visitor.controller.VisitorController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class VisitorListOrderingTest {

    @Test
    void adminListPrioritizesPendingReviewThenNewestSubmission() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        new VisitorController(jdbcTemplate).visitors();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture());
        assertTrue(sql.getValue().contains(
                "ORDER BY CASE WHEN appointment_status = 'PENDING' THEN 0 ELSE 1 END"
        ));
        assertTrue(sql.getValue().contains("created_at DESC, id DESC"));
    }
}

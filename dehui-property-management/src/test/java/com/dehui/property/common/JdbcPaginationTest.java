package com.dehui.property.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcPaginationTest {

    @Test
    void queryNormalizesPagingAndReturnsRecordsWithTotal() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(25L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 11L, "name", "row-11"),
                Map.of("id", 12L, "name", "row-12")
        ));

        PageResponse<Map<String, Object>> page = JdbcPagination.query(
                jdbcTemplate,
                "SELECT id, name FROM demo WHERE status = ? ORDER BY id ASC",
                "SELECT COUNT(*) FROM demo WHERE status = ?",
                List.of("ACTIVE"),
                2,
                10
        );

        assertEquals(25L, page.total());
        assertEquals(2, page.page());
        assertEquals(10, page.pageSize());
        assertEquals(2, page.records().size());
        assertEquals(11L, page.records().getFirst().get("id"));
        verify(jdbcTemplate).query(
                eq("SELECT id, name FROM demo WHERE status = ? ORDER BY id ASC LIMIT ? OFFSET ?"),
                any(RowMapper.class),
                eq("ACTIVE"),
                eq(10),
                eq(10)
        );
    }

    @Test
    void invalidPagingUsesSafeDefaultsAndCapsLargePageSize() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        PageResponse<Map<String, Object>> page = JdbcPagination.query(
                jdbcTemplate,
                "SELECT id FROM demo",
                "SELECT COUNT(*) FROM demo",
                List.of(),
                0,
                500
        );

        assertEquals(1, page.page());
        assertEquals(100, page.pageSize());
        verify(jdbcTemplate).query(
                eq("SELECT id FROM demo LIMIT ? OFFSET ?"),
                any(RowMapper.class),
                eq(100),
                eq(0)
        );
    }
}

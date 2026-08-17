package com.dehui.property.modules.tenant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.tenant.application.TenantArchiveService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TenantArchiveServiceSqlTest {

    @Test
    void directoryWithoutFiltersKeepsWhitespaceBeforeOrderBy() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AtomicReference<String> recordSql = new AtomicReference<>();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    recordSql.set(invocation.getArgument(0));
                    return List.of();
                });

        new TenantArchiveService(jdbcTemplate).directory(null, null, 1, 30);

        assertTrue(recordSql.get().contains("tenant_record.deleted = 0\nORDER BY"));
        assertFalse(recordSql.get().contains("tenant_record.deleted = 0ORDER BY"));
    }

    @Test
    void directoryCountsOnlyRoomsThatHaveNotBeenSoftDeleted() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AtomicReference<String> recordSql = new AtomicReference<>();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    recordSql.set(invocation.getArgument(0));
                    return List.of();
                });

        new TenantArchiveService(jdbcTemplate).directory(null, null, 1, 30);

        assertTrue(recordSql.get().contains("COUNT(DISTINCT room_record.id) AS room_count"));
        assertTrue(recordSql.get().contains("room_record.id = room_link.room_id"));
        assertTrue(recordSql.get().contains("room_record.deleted = 0"));
    }

    @Test
    void contractArchiveSelectsCanonicalTenantName() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AtomicReference<String> recordSql = new AtomicReference<>();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    recordSql.set(invocation.getArgument(0));
                    return List.of();
                });

        new TenantArchiveService(jdbcTemplate).contracts(10L, null, 1, 30);

        assertTrue(recordSql.get().contains("tenant_record.name AS tenant_name"));
        assertTrue(recordSql.get().contains("INNER JOIN tenant tenant_record"));
    }

    @Test
    void currentContractFilterExcludesContractsThatHaveNotStarted() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AtomicReference<String> recordSql = new AtomicReference<>();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    recordSql.set(invocation.getArgument(0));
                    return List.of();
                });

        new TenantArchiveService(jdbcTemplate).contracts(10L, "CURRENT", 1, 30);

        assertTrue(recordSql.get().contains(
                "contract_record.status = 'ACTIVE' "
                        + "AND contract_record.start_date <= CURRENT_DATE "
                        + "AND contract_record.end_date >= CURRENT_DATE"
        ));
    }

    @Test
    void overviewOnlyExposesActiveLogoBoundToTheSameTenant() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AtomicReference<String> overviewSql = new AtomicReference<>();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (overviewSql.get() == null) {
                        overviewSql.set(sql);
                        return Map.of("id", 8L);
                    }
                    return Map.of();
                });

        new TenantArchiveService(jdbcTemplate).overview(8L);

        assertTrue(overviewSql.get().contains("logo_file.id = tenant_record.logo_file_id"));
        assertTrue(overviewSql.get().contains("logo_file.biz_type = 'TENANT_LOGO'"));
        assertTrue(overviewSql.get().contains("logo_file.biz_id = tenant_record.id"));
        assertTrue(overviewSql.get().contains("logo_file.access_scope = 'INTERNAL'"));
        assertTrue(overviewSql.get().contains("logo_file.status = 'ENABLED'"));
    }
}

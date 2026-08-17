package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CustomFieldRepositoryEntityLockTest {

    @Test
    void entityExistenceCheckLocksTheParentBeforeReplacingCustomValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CustomFieldWorkspaceRegistry.Registration bills = new CustomFieldWorkspaceRegistry().require("bills");
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql.replaceAll("\\s+", " ").toUpperCase().contains("FOR UPDATE")),
                eq(Long.class),
                eq(91L)
        )).thenReturn(List.of(91L));

        boolean exists = new CustomFieldRepository(jdbcTemplate, new ObjectMapper()).entityExists(bills, 91L);

        assertTrue(exists);
        verify(jdbcTemplate).queryForList(
                argThat(sql -> {
                    String normalized = sql.replaceAll("\\s+", " ").toUpperCase();
                    return normalized.contains("SELECT ID FROM BILL")
                            && normalized.contains("DELETED = 0")
                            && normalized.contains("FOR UPDATE");
                }),
                eq(Long.class),
                eq(91L)
        );
    }

    @Test
    void batchParentLocksAreSortedToPreventOppositeOrderDeadlocks() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CustomFieldWorkspaceRegistry.Registration bills = new CustomFieldWorkspaceRegistry().require("bills");
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql.replaceAll("\\s+", " ").toUpperCase().contains("ORDER BY ID FOR UPDATE")),
                eq(Long.class),
                eq(41L),
                eq(92L)
        )).thenReturn(List.of(41L, 92L));

        var locked = new CustomFieldRepository(jdbcTemplate, new ObjectMapper())
                .lockEntitiesForUpdate(bills, List.of(92L, 41L, 92L));

        assertEquals(List.of(41L, 92L), locked.stream().toList());
    }
}

package com.dehui.property.modules.workorder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.workorder.controller.WorkOrderController;
import com.dehui.property.security.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class WorkOrderCustomFilterQueryTest {

    @Test
    void customFilterPredicateAppliesToRecordsAndTotal() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CustomFieldQueryService queryService = mock(CustomFieldQueryService.class);
        String customFilters = "[{\"key\":\"custom.priority_label\",\"operator\":\"equals\",\"value\":\"urgent\"}]";
        String predicate = "EXISTS (SELECT 1 FROM custom_field_value cfv WHERE cfv.entity_id = w.id AND cfv.value_text = ?)";
        when(queryService.compile(
                eq("workorders"),
                eq(customFilters),
                isNull(),
                isNull(),
                any(NativeOrderPolicy.class)
        )).thenReturn(new CompiledQuery(predicate, List.of("urgent"), List.of(), List.of(), 1));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        WorkOrderController controller = new WorkOrderController(jdbcTemplate, queryService, null);
        AuthPrincipal principal = new AuthPrincipal(
                1L, "admin", "INTERNAL", null, List.of("ADMIN"), List.of("workorder:view")
        );

        controller.workorders(
                principal,
                null, null, null, null, null, null, null, null, null,
                customFilters, null, null, 1, 20
        );

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), any(Object[].class));
        ArgumentCaptor<String> recordsSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(recordsSql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(countSql.getValue().contains(predicate));
        assertTrue(recordsSql.getValue().contains(predicate));
    }
}

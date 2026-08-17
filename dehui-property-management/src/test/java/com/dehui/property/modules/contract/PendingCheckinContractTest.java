package com.dehui.property.modules.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.contract.controller.ContractController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PendingCheckinContractTest {

    @Test
    void pendingCheckinExcludesContractsWhoseTenantIsDeletedOrDisabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("JOIN tenant t ON t.id = c.tenant_id"), eq(Long.class)))
                .thenReturn(List.of(1L));
        when(jdbcTemplate.queryForList(contains("LEFT JOIN tenant t ON t.id = c.tenant_id")))
                .thenReturn(List.of(
                        Map.of("id", 1L, "status", "ACTIVE"),
                        Map.of("id", 2L, "status", "ACTIVE")
                ));
        when(jdbcTemplate.queryForList(contains("FROM contract_room cr"), any(Object[].class)))
                .thenReturn(List.of());

        ApiResponse<List<Map<String, Object>>> response = new ContractController(jdbcTemplate)
                .pendingCheckinContracts();

        assertEquals(List.of(1L), response.data().stream().map(row -> ((Number) row.get("id")).longValue()).toList());
    }
}

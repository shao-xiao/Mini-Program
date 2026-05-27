package com.dehui.property.modules.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.ai.controller.DailyReportController;
import com.dehui.property.modules.bill.controller.BillController;
import com.dehui.property.modules.contract.controller.ContractController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DashboardSupportEndpointsTest {

    @Test
    void contractsEndpointReturnsEmptyListForDashboard() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        ApiResponse<List<Map<String, Object>>> response = new ContractController(jdbcTemplate).contracts();

        assertEquals(200, response.code());
        assertTrue(response.data().isEmpty());
    }

    @Test
    void billsEndpointReturnsEmptyListForDashboard() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        ApiResponse<PageResponse<Map<String, Object>>> response = new BillController(jdbcTemplate)
                .bills(null, null, null, null, null, 1, 20);

        assertEquals(200, response.code());
        assertTrue(response.data().records().isEmpty());
        assertEquals(0L, response.data().total());
    }

    @Test
    void dailyReportEndpointReturnsDashboardSafePayload() {
        ApiResponse<Map<String, Object>> response = new DailyReportController().dailyReport();

        assertEquals(200, response.code());
        assertTrue(response.data().containsKey("metrics"));
        assertTrue(response.data().containsKey("riskItems"));
        assertTrue(response.data().containsKey("actionItems"));
    }

    @Test
    void dailyReportCompanionEndpointsReturnSafePayloads() {
        DailyReportController controller = new DailyReportController();

        assertEquals(200, controller.refreshDailyReport().code());
        assertTrue(controller.history().data().isEmpty());
        assertEquals(200, controller.detail(1L).code());
    }
}

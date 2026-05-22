package com.dehui.property.modules.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.ai.controller.DailyReportController;
import com.dehui.property.modules.bill.controller.BillController;
import com.dehui.property.modules.contract.controller.ContractController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DashboardSupportEndpointsTest {

    @Test
    void contractsEndpointReturnsEmptyListForDashboard() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT id, code, tenant_id AS tenantId, room_id AS roomId, start_date AS startDate, end_date AS endDate, rent_amount AS rentAmount, deposit_amount AS depositAmount, status FROM contract WHERE deleted = 0 ORDER BY updated_at DESC, id DESC"))
                .thenReturn(List.of());

        ApiResponse<List<Map<String, Object>>> response = new ContractController(jdbcTemplate).contracts();

        assertEquals(200, response.code());
        assertTrue(response.data().isEmpty());
    }

    @Test
    void billsEndpointReturnsEmptyListForDashboard() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT id, code, tenant_id AS tenantId, contract_id AS contractId, source_type AS sourceType, bill_type AS billType, amount, paid_amount AS paidAmount, due_date AS dueDate, audit_status AS auditStatus, pay_status AS payStatus, status FROM bill WHERE deleted = 0 ORDER BY due_date DESC, updated_at DESC, id DESC"))
                .thenReturn(List.of());

        ApiResponse<List<Map<String, Object>>> response = new BillController(jdbcTemplate).bills();

        assertEquals(200, response.code());
        assertTrue(response.data().isEmpty());
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

package com.dehui.property.modules.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.parking.controller.ParkingController;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class ParkingBillingV36BehaviorTest {

    @Test
    void concurrentUniqueConflictBecomesSkippedWhenWinnerExists() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("2026-08")))
                .thenReturn(List.of(activeAssignment(77L)));
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("uk_parking_bill_active_month"));

        ApiResponse<Map<String, Object>> response = new ParkingController(jdbcTemplate)
                .syncBills(Map.of("month", "2026-08"));

        assertEquals(0, response.data().get("successCount"));
        assertEquals(1, response.data().get("skippedCount"));
        assertEquals(0, response.data().get("failureCount"));
        assertTrue(response.data().get("errors").toString().contains("并发"));
    }

    @Test
    void unrelatedDuplicateKeyStillRollsBackAsInfrastructureFailure() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("2026-08")))
                .thenReturn(List.of(activeAssignment(78L)));
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("uk_parking_bill_code"));

        assertThrows(
                DuplicateKeyException.class,
                () -> new ParkingController(jdbcTemplate).syncBills(Map.of("month", "2026-08"))
        );
    }

    private static Map<String, Object> activeAssignment(Long id) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("tenantId", 6L);
        row.put("parkingSpaceId", 1L);
        row.put("startDate", LocalDate.of(2026, 1, 1));
        row.put("endDate", null);
        row.put("status", "ACTIVE");
        row.put("monthlyFee", new BigDecimal("123.45"));
        row.put("resolvedSpaceId", 1L);
        row.put("resolvedTenantId", 6L);
        row.put("hasActiveBill", 0);
        row.put("hasUnresolvedActiveBill", 0);
        return row;
    }
}

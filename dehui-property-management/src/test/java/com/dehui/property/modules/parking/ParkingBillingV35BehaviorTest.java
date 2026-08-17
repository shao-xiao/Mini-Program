package com.dehui.property.modules.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.parking.controller.ParkingController;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class ParkingBillingV35BehaviorTest {

    @Test
    void manualBillPersistsExplicitBillingMonth() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        new ParkingController(jdbcTemplate).createBill(Map.of(
                "assignmentId", 9L,
                "billingMonth", "2026-07",
                "amount", "680.00",
                "dueDate", "2026-08-10"
        ));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("billing_month"));
        assertTrue(Arrays.asList(args.getValue()).contains("2026-07"));
    }

    @Test
    void legacyManualBillDerivesBillingMonthFromDueDate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        new ParkingController(jdbcTemplate).createBill(Map.of(
                "assignmentId", 9L,
                "amount", "680.00",
                "dueDate", "2026-08-10"
        ));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), args.capture());
        assertTrue(Arrays.asList(args.getValue()).contains("2026-08"));
    }

    @Test
    void invalidBillingMonthIsRejectedBeforeWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new ParkingController(jdbcTemplate).createBill(Map.of(
                        "assignmentId", 9L,
                        "billingMonth", "2026-13",
                        "amount", "680.00",
                        "dueDate", "2026-08-10"
                )));

        assertEquals(400, exception.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void manualBillRejectsAnExistingActiveAssignmentMonth() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                eq(9L),
                eq("2026-07")
        )).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new ParkingController(jdbcTemplate).createBill(Map.of(
                        "assignmentId", 9L,
                        "billingMonth", "2026-07",
                        "amount", "680.00",
                        "dueDate", "2026-08-10"
                )));

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void manualBillFailsClosedWhenAssignmentHasUnresolvedActiveHistory() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                eq(9L)
        )).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new ParkingController(jdbcTemplate).createBill(Map.of(
                        "assignmentId", 9L,
                        "billingMonth", "2026-07",
                        "amount", "680.00",
                        "dueDate", "2026-08-10"
                )));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("unresolved billing month"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void bindingPersistsPositiveMonthlyFee() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L, 0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 5L, "monthly_rate", new BigDecimal("680.50"))));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        new ParkingController(jdbcTemplate).bind(5L, Map.of(
                "partyId", 18L,
                "plateNo", "TEST-001",
                "startDate", "2026-07-01",
                "monthlyFee", "680.50"
        ));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), args.capture());
        int assignmentInsert = indexContaining(sql.getAllValues(), "INSERT INTO parking_assignment");
        assertTrue(assignmentInsert >= 0);
        assertTrue(Arrays.asList(args.getAllValues().get(assignmentInsert)).contains(new BigDecimal("680.50")));
    }

    @Test
    void bindingRejectsMissingZeroAndNegativeMonthlyFees() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ParkingController controller = new ParkingController(jdbcTemplate);
        Map<String, Object> base = new HashMap<>();
        base.put("partyId", 18L);
        base.put("plateNo", "TEST-001");
        base.put("startDate", "2026-07-01");

        BusinessException missing = assertThrows(BusinessException.class, () -> controller.bind(5L, base));
        BusinessException zero = assertThrows(BusinessException.class, () -> {
            Map<String, Object> body = new HashMap<>(base);
            body.put("monthlyFee", 0);
            controller.bind(5L, body);
        });
        BusinessException negative = assertThrows(BusinessException.class, () -> {
            Map<String, Object> body = new HashMap<>(base);
            body.put("monthlyFee", -1);
            controller.bind(5L, body);
        });

        assertEquals(400, missing.getCode());
        assertEquals(400, zero.getCode());
        assertEquals(400, negative.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void syncClassifiesSuccessSkippedAndFailureWithoutTrustingClientAmount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("2026-07"))).thenReturn(List.of(
                assignment(1L, "2026-01-01", null, "ACTIVE", "680.00", true, true, false),
                assignment(2L, "2026-01-01", null, "ACTIVE", "700.00", true, true, true),
                assignment(3L, "2026-07-15", null, "ACTIVE", "720.00", true, true, false),
                assignment(4L, "2026-01-01", null, "ACTIVE", null, true, true, false),
                assignment(5L, "2026-01-01", null, "ACTIVE", "760.00", true, false, false)
        ));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Map<String, Object>> response = new ParkingController(jdbcTemplate).syncBills(Map.of(
                "month", "2026-07",
                "amount", "9999.00",
                "monthlyFee", "9999.00"
        ));

        assertEquals(1, response.data().get("generatedCount"));
        assertEquals(1, response.data().get("successCount"));
        assertEquals(2, response.data().get("skippedCount"));
        assertEquals(2, response.data().get("failureCount"));
        assertEquals(4, ((List<?>) response.data().get("errors")).size());

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), args.capture());
        List<Object> values = Arrays.asList(args.getValue());
        assertTrue(values.contains(new BigDecimal("680.00")));
        assertTrue(!values.contains(new BigDecimal("9999.00")));
        assertTrue(values.contains("2026-07"));
    }

    @Test
    void invalidSyncMonthRejectsWholeRequest() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).syncBills(Map.of("month", "2026-7"))
        );

        assertEquals(400, exception.getCode());
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void syncSkipsPartialMonthAndInactiveAssignmentsWithReasons() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("2026-07"))).thenReturn(List.of(
                assignment(1L, "2026-07-02", null, "ACTIVE", "680.00", true, true, false),
                assignment(2L, "2026-01-01", "2026-07-30", "ACTIVE", "680.00", true, true, false),
                assignment(3L, "2026-01-01", null, "RELEASED", "680.00", true, true, false)
        ));

        Map<String, Object> result = new ParkingController(jdbcTemplate)
                .syncBills(Map.of("month", "2026-07"))
                .data();

        assertEquals(0, result.get("successCount"));
        assertEquals(3, result.get("skippedCount"));
        assertEquals(0, result.get("failureCount"));
        List<?> issues = (List<?>) result.get("errors");
        assertEquals(3, issues.size());
        assertTrue(issues.toString().contains("需要人工计费"));
        assertTrue(issues.toString().contains("已失效"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void syncClassifiesMissingZeroAndNegativeServerFeesAsFailures() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("2026-07"))).thenReturn(List.of(
                assignment(1L, "2026-01-01", null, "ACTIVE", null, true, true, false),
                assignment(2L, "2026-01-01", null, "ACTIVE", "0.00", true, true, false),
                assignment(3L, "2026-01-01", null, "ACTIVE", "-1.00", true, true, false)
        ));

        Map<String, Object> result = new ParkingController(jdbcTemplate)
                .syncBills(Map.of("month", "2026-07"))
                .data();

        assertEquals(0, result.get("successCount"));
        assertEquals(0, result.get("skippedCount"));
        assertEquals(3, result.get("failureCount"));
        assertTrue(result.get("errors").toString().contains("未配置停车月费"));
        assertTrue(result.get("errors").toString().contains("必须大于 0"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void syncFailsClosedWhenActiveLegacyBillHasNoConfirmedBillingMonth() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> candidate = assignment(
                1L,
                "2026-01-01",
                null,
                "ACTIVE",
                "680.00",
                true,
                true,
                false
        );
        candidate.put("hasUnresolvedActiveBill", 1);
        when(jdbcTemplate.queryForList(anyString(), eq("2026-07"))).thenReturn(List.of(candidate));

        Map<String, Object> result = new ParkingController(jdbcTemplate)
                .syncBills(Map.of("month", "2026-07"))
                .data();

        assertEquals(0, result.get("successCount"));
        assertEquals(1, result.get("failureCount"));
        assertTrue(result.get("errors").toString().contains("账期未确认"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void syncDeclaresTransactionBoundaryForInfrastructureRollback() throws Exception {
        Transactional transactional = ParkingController.class
                .getMethod("syncBills", Map.class)
                .getAnnotation(Transactional.class);

        assertTrue(transactional != null);
    }

    private static Map<String, Object> assignment(
            Long id,
            String startDate,
            String endDate,
            String status,
            String monthlyFee,
            boolean spaceValid,
            boolean tenantValid,
            boolean hasActiveBill) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("startDate", LocalDate.parse(startDate));
        row.put("endDate", endDate == null ? null : LocalDate.parse(endDate));
        row.put("status", status);
        row.put("monthlyFee", monthlyFee == null ? null : new BigDecimal(monthlyFee));
        row.put("resolvedSpaceId", spaceValid ? 100L + id : null);
        row.put("resolvedTenantId", tenantValid ? 200L + id : null);
        row.put("hasActiveBill", hasActiveBill ? 1 : 0);
        return row;
    }

    private static int indexContaining(List<String> values, String expected) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).contains(expected)) {
                return index;
            }
        }
        return -1;
    }
}

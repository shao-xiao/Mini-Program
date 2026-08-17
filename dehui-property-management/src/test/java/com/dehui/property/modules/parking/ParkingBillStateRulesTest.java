package com.dehui.property.modules.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ParkingBillStateRulesTest {

    @Test
    void payUsesAtomicStateAndAmountConditions() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(9L))).thenReturn(List.of(unpaidActive("680.00")));
        when(jdbcTemplate.update(anyString(), eq(0L), eq(9L))).thenReturn(1);

        new ParkingController(jdbcTemplate).payBill(9L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(0L), eq(9L));
        assertTrue(sql.getValue().contains("pay_status = 'UNPAID'"));
        assertTrue(sql.getValue().contains("status IN ('ACTIVE', 'ENABLED')"));
        assertTrue(sql.getValue().contains("amount > 0"));
        assertTrue(sql.getValue().contains("deleted = 0"));
    }

    @Test
    void paidBillCannotBePaidAgain() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(9L))).thenReturn(List.of(bill("PAID", "ACTIVE", 0, "680.00")));
        when(jdbcTemplate.update(anyString(), eq(0L), eq(9L))).thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).payBill(9L)
        );

        assertEquals(409, exception.getCode());
        assertEquals("Parking bill has already been paid", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), eq(0L), eq(9L));
    }

    @Test
    void voidedBillCannotBePaid() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(9L))).thenReturn(List.of(bill("UNPAID", "VOID", 0, "680.00")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).payBill(9L)
        );

        assertEquals(409, exception.getCode());
        assertEquals("Voided parking bill cannot be paid", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), eq(0L), eq(9L));
    }

    @Test
    void nonPositiveBillCannotBePaid() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(9L))).thenReturn(List.of(unpaidActive("0.00")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).payBill(9L)
        );

        assertEquals(409, exception.getCode());
        assertEquals("Parking bill amount is invalid", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), eq(0L), eq(9L));
    }

    @Test
    void deletedAndMissingBillsCannotBePaid() {
        JdbcTemplate deletedJdbc = mock(JdbcTemplate.class);
        when(deletedJdbc.queryForList(anyString(), eq(9L))).thenReturn(List.of(bill("UNPAID", "ACTIVE", 1, "680.00")));
        JdbcTemplate missingJdbc = mock(JdbcTemplate.class);
        when(missingJdbc.queryForList(anyString(), eq(10L))).thenReturn(List.of());

        BusinessException deleted = assertThrows(
                BusinessException.class,
                () -> new ParkingController(deletedJdbc).payBill(9L)
        );
        BusinessException missing = assertThrows(
                BusinessException.class,
                () -> new ParkingController(missingJdbc).payBill(10L)
        );

        assertEquals(404, deleted.getCode());
        assertEquals(404, missing.getCode());
        verify(deletedJdbc, never()).update(anyString(), eq(0L), eq(9L));
        verify(missingJdbc, never()).update(anyString(), eq(0L), eq(10L));
    }

    @Test
    void repeatedPaymentOnlyUpdatesOnce() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(9L)))
                .thenReturn(List.of(unpaidActive("680.00")))
                .thenReturn(List.of(bill("PAID", "ACTIVE", 0, "680.00")));
        when(jdbcTemplate.update(anyString(), eq(0L), eq(9L))).thenReturn(1);
        ParkingController controller = new ParkingController(jdbcTemplate);

        controller.payBill(9L);
        BusinessException exception = assertThrows(BusinessException.class, () -> controller.payBill(9L));

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate).update(anyString(), eq(0L), eq(9L));
    }

    @Test
    void paymentLosesRaceWhenVoidTransitionWins() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(9L)))
                .thenReturn(List.of(unpaidActive("680.00")))
                .thenReturn(List.of(bill("UNPAID", "VOID", 0, "680.00")));
        when(jdbcTemplate.update(anyString(), eq(0L), eq(9L))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).payBill(9L)
        );

        assertEquals(409, exception.getCode());
        assertEquals("Voided parking bill cannot be paid", exception.getMessage());
    }

    @Test
    void voidUsesAtomicUnpaidActiveConditions() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(9L))).thenReturn(List.of(unpaidActive("680.00")));
        when(jdbcTemplate.update(anyString(), eq(0L), eq(9L))).thenReturn(1);

        new ParkingController(jdbcTemplate).voidBill(9L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(0L), eq(9L));
        assertTrue(sql.getValue().contains("pay_status = 'UNPAID'"));
        assertTrue(sql.getValue().contains("status IN ('ACTIVE', 'ENABLED')"));
        assertTrue(sql.getValue().contains("deleted = 0"));
    }

    @Test
    void paidAndAlreadyVoidedBillsCannotBeVoided() {
        JdbcTemplate paidJdbc = mock(JdbcTemplate.class);
        when(paidJdbc.queryForList(anyString(), eq(9L))).thenReturn(List.of(bill("PAID", "ACTIVE", 0, "680.00")));
        JdbcTemplate voidedJdbc = mock(JdbcTemplate.class);
        when(voidedJdbc.queryForList(anyString(), eq(10L))).thenReturn(List.of(bill("UNPAID", "VOID", 0, "680.00")));

        BusinessException paid = assertThrows(
                BusinessException.class,
                () -> new ParkingController(paidJdbc).voidBill(9L)
        );
        BusinessException voided = assertThrows(
                BusinessException.class,
                () -> new ParkingController(voidedJdbc).voidBill(10L)
        );

        assertEquals(409, paid.getCode());
        assertEquals("Paid parking bill cannot be voided", paid.getMessage());
        assertEquals(409, voided.getCode());
        assertEquals("Parking bill is already voided", voided.getMessage());
        verify(paidJdbc, never()).update(anyString(), eq(0L), eq(9L));
        verify(voidedJdbc, never()).update(anyString(), eq(0L), eq(10L));
    }

    @Test
    void voidLosesRaceWhenPaymentTransitionWins() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(9L)))
                .thenReturn(List.of(unpaidActive("680.00")))
                .thenReturn(List.of(bill("PAID", "ACTIVE", 0, "680.00")));
        when(jdbcTemplate.update(anyString(), eq(0L), eq(9L))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).voidBill(9L)
        );

        assertEquals(409, exception.getCode());
        assertEquals("Paid parking bill cannot be voided", exception.getMessage());
    }

    @Test
    void deletedBillCannotBeVoided() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(9L))).thenReturn(List.of(bill("UNPAID", "ACTIVE", 1, "680.00")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).voidBill(9L)
        );

        assertEquals(404, exception.getCode());
        verify(jdbcTemplate, never()).update(anyString(), eq(0L), eq(9L));
    }

    @Test
    void createBillAtomicallyResolvesAssignmentOwnership() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1);

        new ParkingController(jdbcTemplate).createBill(Map.of(
                "assignmentId", 9L,
                "tenantId", 999L,
                "amount", "680.50",
                "dueDate", "2026-07-31"
        ));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("INSERT INTO parking_bill"));
        assertTrue(sql.getValue().contains("SELECT"));
        assertTrue(sql.getValue().contains("FROM parking_assignment pa"));
        assertTrue(sql.getValue().contains("JOIN parking_space ps"));
        assertTrue(sql.getValue().contains("JOIN tenant t"));
        assertTrue(Arrays.asList(args.getValue()).contains(9L));
        assertTrue(!Arrays.asList(args.getValue()).contains(999L));
    }

    @Test
    void createBillRejectsAmountBeyondDatabaseScale() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).createBill(Map.of(
                        "assignmentId", 9L,
                        "amount", "10.001",
                        "dueDate", "2026-07-31"
                ))
        );

        assertEquals(400, exception.getCode());
        assertEquals("Parking bill amount supports at most 2 decimal places", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void malformedAssignmentIdReturnsBusinessValidationError() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).createBill(Map.of(
                        "assignmentId", "not-an-id",
                        "amount", "680.00",
                        "dueDate", "2026-07-31"
                ))
        );

        assertEquals(400, exception.getCode());
        assertEquals("Parking assignment is invalid", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void syncDoesNotRequireOrTrustClientAmount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("2026-07"))).thenReturn(List.of());

        ApiResponse<Map<String, Object>> response = new ParkingController(jdbcTemplate).syncBills(Map.of(
                "month", "2026-07",
                "amount", 0,
                "monthlyFee", 0
        ));

        assertEquals(0, response.data().get("generatedCount"));
        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void syncQueryLoadsServerOwnedFeeRelationsAndExistingBillState() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("2026-07"))).thenReturn(List.of());

        ApiResponse<Map<String, Object>> response = new ParkingController(jdbcTemplate)
                .syncBills(Map.of("month", "2026-07"));

        ArgumentCaptor<String> querySql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(querySql.capture(), eq("2026-07"));
        assertTrue(querySql.getValue().contains("pa.monthly_fee AS monthlyFee"));
        assertTrue(querySql.getValue().contains("LEFT JOIN parking_space ps"));
        assertTrue(querySql.getValue().contains("LEFT JOIN tenant t"));
        assertTrue(querySql.getValue().contains("pb.billing_month = ?"));
        assertTrue(querySql.getValue().contains("legacy_pb.billing_month IS NULL"));
        assertEquals(0, response.data().get("generatedCount"));
    }

    @Test
    void malformedAmountReturnsBusinessValidationError() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate).createBill(Map.of(
                        "assignmentId", 9L,
                        "amount", "not-a-number",
                        "dueDate", "2026-07-31"
                ))
        );

        assertEquals(400, exception.getCode());
        assertEquals("Parking bill amount is invalid", exception.getMessage());
    }

    private static Map<String, Object> unpaidActive(String amount) {
        return bill("UNPAID", "ACTIVE", 0, amount);
    }

    private static Map<String, Object> bill(String payStatus, String status, int deleted, String amount) {
        return Map.of(
                "payStatus", payStatus,
                "status", status,
                "deleted", deleted,
                "amount", new BigDecimal(amount)
        );
    }
}

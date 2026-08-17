package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.bill.application.BillCommandService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class BillCommandServiceTest {

    @Test
    void legalPaymentUsesAuthenticatedOperatorAndCreatesOnePaymentRecord() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FOR UPDATE"), eq(9L)))
                .thenReturn(List.of(bill("APPROVED", "UNPAID", "ACTIVE")));
        when(jdbcTemplate.queryForObject(contains("bill_payment_record"), eq(Integer.class), eq(9L)))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("UPDATE bill"), any(Object[].class))).thenReturn(1);

        new BillCommandService(jdbcTemplate).confirmOfflinePayment(9L, 7001L);

        ArgumentCaptor<Object[]> paymentArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO bill_payment_record"), paymentArgs.capture());
        assertEquals(9L, paymentArgs.getValue()[1]);
        assertEquals(7001L, paymentArgs.getValue()[5]);
        assertEquals(7001L, paymentArgs.getValue()[6]);
    }

    @Test
    void repeatedPaymentReturnsConflictWithoutWriting() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FOR UPDATE"), eq(9L)))
                .thenReturn(List.of(bill("APPROVED", "PAID", "ACTIVE")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new BillCommandService(jdbcTemplate).confirmOfflinePayment(9L, 7001L)
        );

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void paymentRejectsUnapprovedRejectedAndInactiveBills() {
        assertPaymentRejected(bill("PENDING", "UNPAID", "ACTIVE"));
        assertPaymentRejected(bill("REJECTED", "UNPAID", "ACTIVE"));
        assertPaymentRejected(bill("APPROVED", "UNPAID", "VOID"));
    }

    @Test
    void paymentMethodDefinesOneTransactionBoundary() throws Exception {
        assertTrue(BillCommandService.class
                .getMethod("confirmOfflinePayment", Long.class, Long.class)
                .isAnnotationPresent(Transactional.class));
    }

    private void assertPaymentRejected(Map<String, Object> state) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FOR UPDATE"), eq(9L))).thenReturn(List.of(state));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new BillCommandService(jdbcTemplate).confirmOfflinePayment(9L, 7001L)
        );

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    private Map<String, Object> bill(String auditStatus, String payStatus, String status) {
        return Map.of(
                "tenant_id", 8L,
                "amount", new BigDecimal("1888.00"),
                "final_amount", new BigDecimal("1888.00"),
                "audit_status", auditStatus,
                "workflow_status", "APPROVED".equals(auditStatus) ? "APPROVED" : "DRAFT",
                "pay_status", payStatus,
                "status", status
        );
    }
}

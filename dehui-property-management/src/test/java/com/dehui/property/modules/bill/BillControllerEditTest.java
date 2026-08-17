package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.bill.controller.BillController;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class BillControllerEditTest {

    @Test
    void billSummaryExcludesCancelledBillsFromEffectiveReceivableAmount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForMap(
                org.mockito.ArgumentMatchers.contains("CASE WHEN b.pay_status <> 'CANCELLED' THEN b.amount ELSE 0 END"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(Map.of(
                "totalCount", 2L,
                "totalAmount", new BigDecimal("100.00"),
                "paidAmount", BigDecimal.ZERO,
                "unpaidAmount", new BigDecimal("100.00"),
                "paidCount", 0L,
                "unpaidCount", 1L,
                "overdueCount", 0L,
                "overdueAmount", BigDecimal.ZERO
        ));

        ApiResponse<Map<String, Object>> response = new BillController(jdbcTemplate).billSummary(null, null, null, null, null);

        assertEquals(200, response.code());
        assertEquals(new BigDecimal("100.00"), response.data().get("totalAmount"));
    }

    @Test
    void updateBillAllowsUnpaidBillAndResetsAuditStatus() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT pay_status AS payStatus, paid_amount AS paidAmount FROM bill WHERE id = ? AND deleted = 0 LIMIT 1", 9L))
                .thenReturn(List.of(Map.of("payStatus", "UNPAID", "paidAmount", BigDecimal.ZERO)));
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contract WHERE id = ? AND tenant_id = ? AND deleted = 0",
                Integer.class,
                3L,
                8L
        )).thenReturn(1);
        when(jdbcTemplate.update("""
                UPDATE bill
                SET code = COALESCE(?, code),
                    tenant_id = ?,
                    contract_id = ?,
                    source_type = ?,
                    source_id = ?,
                    bill_type = ?,
                    title = ?,
                    period_start = ?,
                    period_end = ?,
                    amount = ?,
                    due_date = ?,
                    audit_status = 'PENDING',
                    pay_status = 'UNPAID',
                    status = 'ACTIVE',
                    remark = ?,
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                "BIL-2026-UPDATED",
                8L,
                3L,
                "MANUAL",
                null,
                "PROPERTY",
                "6月物业费",
                "2026-06-01",
                "2026-06-30",
                new BigDecimal("1888.00"),
                "2026-07-05",
                "调整账期和金额",
                0L,
                9L
        )).thenReturn(1);

        ApiResponse<Void> response = new BillController(jdbcTemplate).updateBill(9L, Map.ofEntries(
                Map.entry("billNumber", "BIL-2026-UPDATED"),
                Map.entry("tenantId", 8L),
                Map.entry("contractId", 3L),
                Map.entry("sourceType", "MANUAL"),
                Map.entry("billType", "PROPERTY"),
                Map.entry("title", "6月物业费"),
                Map.entry("periodStart", "2026-06-01"),
                Map.entry("periodEnd", "2026-06-30"),
                Map.entry("amount", new BigDecimal("1888.00")),
                Map.entry("dueDate", "2026-07-05"),
                Map.entry("remark", "调整账期和金额")
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("""
                UPDATE bill
                SET code = COALESCE(?, code),
                    tenant_id = ?,
                    contract_id = ?,
                    source_type = ?,
                    source_id = ?,
                    bill_type = ?,
                    title = ?,
                    period_start = ?,
                    period_end = ?,
                    amount = ?,
                    due_date = ?,
                    audit_status = 'PENDING',
                    pay_status = 'UNPAID',
                    status = 'ACTIVE',
                    remark = ?,
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                "BIL-2026-UPDATED",
                8L,
                3L,
                "MANUAL",
                null,
                "PROPERTY",
                "6月物业费",
                "2026-06-01",
                "2026-06-30",
                new BigDecimal("1888.00"),
                "2026-07-05",
                "调整账期和金额",
                0L,
                9L
        );
    }

    @Test
    void updateBillRejectsBlankExplicitBillNumber() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT pay_status AS payStatus, paid_amount AS paidAmount FROM bill WHERE id = ? AND deleted = 0 LIMIT 1", 9L))
                .thenReturn(List.of(Map.of("payStatus", "UNPAID", "paidAmount", BigDecimal.ZERO)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new BillController(jdbcTemplate).updateBill(9L, Map.of(
                        "billNumber", " ",
                        "tenantId", 8L,
                        "amount", 100
                ))
        );

        assertEquals(400, exception.getCode());
        assertEquals("账单编号不能为空", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void updateBillReturnsConflictWhenBillNumberAlreadyExists() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT pay_status AS payStatus, paid_amount AS paidAmount FROM bill WHERE id = ? AND deleted = 0 LIMIT 1", 9L))
                .thenReturn(List.of(Map.of("payStatus", "UNPAID", "paidAmount", BigDecimal.ZERO)));
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("uk_bill_code"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new BillController(jdbcTemplate).updateBill(9L, Map.of(
                        "billNumber", "BIL-DUPLICATE",
                        "tenantId", 8L,
                        "amount", 100
                ))
        );

        assertEquals(409, exception.getCode());
        assertEquals("账单编号已存在", exception.getMessage());
    }

    @Test
    void updateBillRejectsPaidBill() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT pay_status AS payStatus, paid_amount AS paidAmount FROM bill WHERE id = ? AND deleted = 0 LIMIT 1", 9L))
                .thenReturn(List.of(Map.of("payStatus", "PAID", "paidAmount", new BigDecimal("100.00"))));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new BillController(jdbcTemplate).updateBill(9L, Map.of("tenantId", 8L, "amount", 100))
        );

        assertEquals(400, exception.getCode());
        assertEquals("已收款账单不能编辑", exception.getMessage());
    }
}

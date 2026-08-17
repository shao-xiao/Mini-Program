package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.bill.controller.BillController;
import com.dehui.property.security.AuthPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class BillFinanceWorkspaceTest {

    @Test
    void tenantDirectoryUsesOneAggregateQueryAndHidesContactsWithoutTenantPermission() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> tenant = new LinkedHashMap<>();
        tenant.put("id", 8L);
        tenant.put("tenantCode", "TEN-008");
        tenant.put("tenantName", "测试租户");
        tenant.put("contactPerson", "联系人");
        tenant.put("contactPhone", "13800000000");
        tenant.put("activeContractCount", 1);
        tenant.put("unpaidBillCount", 2);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(tenant));

        AuthPrincipal principal = new AuthPrincipal(
                100L, "finance", "SYSTEM", null, List.of("FINANCE"), List.of("bill:view")
        );
        List<Map<String, Object>> result = new BillController(jdbcTemplate)
                .billTenantDirectory(principal)
                .data();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("GROUP BY tenant_id"));
        assertTrue(sql.contains("activeContractCount"));
        assertTrue(sql.contains("unpaidBillCount"));
        assertFalse(result.getFirst().containsKey("contactPerson"));
        assertFalse(result.getFirst().containsKey("contactPhone"));
    }

    @Test
    void paymentAndInvoiceLedgersAreServerPagedByTenantAndContract() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(19L), eq(8L))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        BillController controller = new BillController(jdbcTemplate);

        controller.billPayments(8L, 19L, 1, 20);
        controller.billInvoices(8L, 19L, 1, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, Mockito.times(2)).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        List<String> sqlStatements = sqlCaptor.getAllValues();
        assertTrue(sqlStatements.get(0).contains("p.tenant_id = ?"));
        assertTrue(sqlStatements.get(0).contains("b.contract_id = ?"));
        assertTrue(sqlStatements.get(0).contains("AS paymentNumber"));
        assertTrue(sqlStatements.get(1).contains("i.tenant_id = ?"));
        assertTrue(sqlStatements.get(1).contains("b.contract_id = ?"));
        assertTrue(sqlStatements.get(1).contains("AS invoiceNumber"));
    }

    @Test
    void financeQueriesRejectAContractOwnedByAnotherTenant() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(19L), eq(8L))).thenReturn(0);
        BillController controller = new BillController(jdbcTemplate);

        BusinessException paymentException = assertThrows(
                BusinessException.class,
                () -> controller.billPayments(8L, 19L, 1, 20)
        );
        BusinessException invoiceException = assertThrows(
                BusinessException.class,
                () -> controller.billInvoices(8L, 19L, 1, 20)
        );

        assertEquals(400, paymentException.getCode());
        assertEquals(400, invoiceException.getCode());
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void billCreationRejectsAContractOwnedByAnotherTenant() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(19L), eq(8L))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new BillController(jdbcTemplate).createBill(Map.of(
                        "tenantId", 8L,
                        "contractId", 19L,
                        "amount", "100.00"
                ))
        );

        assertEquals(400, exception.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}

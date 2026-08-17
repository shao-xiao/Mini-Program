package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.bill.controller.BillController;
import com.dehui.property.modules.bill.application.BillCommandService;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class BillControllerWorkspaceTest {

    @Test
    void fullDependencyConstructorIsSelectedForRuntimeInjection() throws Exception {
        var constructor = BillController.class.getDeclaredConstructor(
                JdbcTemplate.class,
                CustomFieldQueryService.class,
                CustomFieldProjectionService.class,
                BillCommandService.class
        );

        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    void billListSupportsWorkspaceFiltersGroupingAndOrdering() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

        new BillController(jdbcTemplate).bills(
                null, null, "RENT", "PENDING", null,
                "BIL", "月租", "德汇", "CON", "CONTRACT", "INVOICED",
                null, new BigDecimal("1000"), new BigDecimal("5000"),
                null, null, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                true, "核验",
                null, null, "tenantName:asc,dueDate:desc", "tenantName:asc",
                1, 20
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        String sql = sqlCaptor.getValue();

        assertTrue(sql.contains("c.code AS contractNumber"));
        assertTrue(sql.contains("b.pay_status AS status"));
        assertTrue(sql.contains("DATEDIFF(CURRENT_DATE, b.due_date)"));
        assertTrue(sql.contains("b.code LIKE ?"));
        assertTrue(sql.contains("t.name LIKE ?"));
        assertTrue(sql.contains("c.code LIKE ?"));
        assertTrue(sql.contains("b.amount >= ?"));
        assertTrue(sql.contains("b.amount <= ?"));
        assertTrue(sql.contains("b.due_date >= ?"));
        assertTrue(sql.contains("b.due_date <= ?"));
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM bill_invoice"));
        assertTrue(sql.contains("ORDER BY t.name ASC, b.due_date DESC"));
    }

    @Test
    void billSummaryUsesTheSameWorkspaceFilterContract() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "totalCount", 0,
                "totalAmount", BigDecimal.ZERO,
                "paidAmount", BigDecimal.ZERO,
                "unpaidAmount", BigDecimal.ZERO,
                "paidCount", 0,
                "unpaidCount", 0,
                "overdueCount", 0,
                "overdueAmount", BigDecimal.ZERO
        ));

        new BillController(jdbcTemplate).billSummary(
                null, null, "RENT", "PENDING", null,
                "BIL", "月租", "德汇", "CON", "CONTRACT", "NONE",
                null, new BigDecimal("1000"), new BigDecimal("5000"),
                null, null, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                false, "核验"
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();

        assertTrue(sql.contains("LEFT JOIN contract c"));
        assertTrue(sql.contains("b.bill_type = ?"));
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM bill_invoice"));
        assertTrue(sql.contains("b.due_date >= ?"));
        assertTrue(sql.contains("b.due_date <= ?"));
        assertTrue(sql.contains("b.pay_status IN ('PAID', 'CANCELLED')"));
    }
}

package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.bill.controller.BillController;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BillAmountValidationTest {

    @Test
    void createBillRejectsZeroAmountBeforeInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new BillController(jdbcTemplate).createBill(Map.of("tenantId", 8L, "amount", 0))
        );

        assertEquals(400, exception.getCode());
        assertEquals("账单金额必须大于0", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void updateBillRejectsNegativeAmountBeforeUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT pay_status AS payStatus, paid_amount AS paidAmount FROM bill WHERE id = ? AND deleted = 0 LIMIT 1",
                9L
        )).thenReturn(List.of(Map.of("payStatus", "UNPAID", "paidAmount", BigDecimal.ZERO)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new BillController(jdbcTemplate).updateBill(9L, Map.of("tenantId", 8L, "amount", -1))
        );

        assertEquals(400, exception.getCode());
        assertEquals("账单金额必须大于0", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}

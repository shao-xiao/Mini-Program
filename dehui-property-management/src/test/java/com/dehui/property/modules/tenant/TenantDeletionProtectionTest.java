package com.dehui.property.modules.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.tenant.controller.TenantController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class TenantDeletionProtectionTest {

    @Test
    void activeBusinessRelationsBlockTenantDeletionWithConcreteReason() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L, 0L, 2L, 0L, 0L, 0L);
        TenantController controller = new TenantController(jdbcTemplate, mock(PasswordEncoder.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.deleteTenant(9L));

        assertEquals(409, exception.getCode());
        assertEquals("租户仍有关联业务，不能删除：有效合同 1 条；未结账单 2 条", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    @Test
    void tenantWithoutActiveRelationsCanBeSoftDeleted() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.update(anyString(), eq(0L), eq(9L))).thenReturn(1);
        TenantController controller = new TenantController(jdbcTemplate, mock(PasswordEncoder.class));

        controller.deleteTenant(9L);

        verify(jdbcTemplate).update(anyString(), eq(0L), eq(9L));
    }
}

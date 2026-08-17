package com.dehui.property.modules.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.tenant.controller.TenantController;
import com.dehui.property.security.AuthPrincipal;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class TenantControllerContactValidationTest {

    @Test
    void saveTenantRejectsInvalidContactPhone() {
        TenantController controller = newController();

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.saveTenant(Map.of(
                "tenantName", "测试租户",
                "contactPhone", "789"
        )));

        assertEquals(400, exception.getCode());
        assertEquals("联系电话格式不正确", exception.getMessage());
    }

    @Test
    void saveTenantRejectsInvalidContactEmail() {
        TenantController controller = newController();

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.saveTenant(Map.of(
                "tenantName", "测试租户",
                "contactPhone", "021-88888888",
                "contactEmail", "的说法来得及试点范围"
        )));

        assertEquals(400, exception.getCode());
        assertEquals("邮箱格式不正确", exception.getMessage());
    }

    @Test
    void saveTenantContactRejectsInvalidPhone() {
        TenantController controller = newController();

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.saveTenantContact(1L, Map.of(
                "name", "张三",
                "phone", "11112312312312123123123"
        )));

        assertEquals(400, exception.getCode());
        assertEquals("联系电话格式不正确", exception.getMessage());
    }

    @Test
    void saveTenantContactAllowsAnotherResponsibleWithoutReplacingThePrimaryContact() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("SELECT id FROM tenant_contact")),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(21L);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        TenantController controller = new TenantController(jdbcTemplate, passwordEncoder);

        ApiResponse<Map<String, Object>> response = controller.saveTenantContact(3L, Map.of(
                "name", "第二负责人",
                "phone", "13800000002",
                "contactRole", "RESPONSIBLE",
                "isPrimary", false,
                "status", "ACTIVE"
        ));

        assertEquals("RESPONSIBLE", response.data().get("contactRole"));
        verify(jdbcTemplate, never()).update(
                argThat((String sql) -> sql != null && sql.contains("role = CASE WHEN role = 'RESPONSIBLE' THEN 'EMPLOYEE'")),
                any(Object[].class)
        );
        ArgumentCaptor<Object[]> insertArguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null && sql.contains("INSERT INTO tenant_contact")),
                insertArguments.capture()
        );
        assertEquals("RESPONSIBLE", insertArguments.getValue()[7]);
        assertEquals(0, insertArguments.getValue()[8]);
    }

    @Test
    void setPrimaryResponsibleKeepsOtherResponsibleRoles() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(22L))).thenReturn(List.of(Map.of(
                "id", 22L,
                "tenantId", 3L,
                "role", "RESPONSIBLE",
                "isPrimary", 0,
                "isEmergency", 0,
                "status", "ACTIVE"
        )));
        TenantController controller = new TenantController(jdbcTemplate, mock(PasswordEncoder.class));

        controller.setResponsibleContact(22L, new AuthPrincipal(
                9L,
                "admin",
                "SYSTEM",
                null,
                List.of("ADMIN"),
                List.of("tenant:update")
        ));

        verify(jdbcTemplate, never()).update(
                argThat((String sql) -> sql != null && sql.contains("role = CASE WHEN role = 'RESPONSIBLE' THEN 'EMPLOYEE'")),
                any(Object[].class)
        );
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null
                        && sql.contains("SET is_primary = 0")
                        && !sql.contains("role = CASE")),
                eq(9L),
                eq(3L),
                eq(22L)
        );
    }

    @Test
    void deleteTenantContactSoftDeletesWithinTenant() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(8L), eq(3L))).thenReturn(List.of(Map.of(
                "id", 8L,
                "tenantId", 3L,
                "role", "EMPLOYEE",
                "isPrimary", 0,
                "status", "ACTIVE"
        )));
        when(jdbcTemplate.update(anyString(), eq(0L), eq(8L), eq(3L))).thenReturn(1);
        TenantController controller = new TenantController(jdbcTemplate, mock(PasswordEncoder.class));

        controller.deleteTenantContact(3L, 8L);

        verify(jdbcTemplate).update(anyString(), eq(0L), eq(8L), eq(3L));
    }

    private TenantController newController() {
        return new TenantController(mock(JdbcTemplate.class), mock(PasswordEncoder.class));
    }
}

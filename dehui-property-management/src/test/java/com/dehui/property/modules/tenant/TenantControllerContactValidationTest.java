package com.dehui.property.modules.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.tenant.controller.TenantController;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantControllerContactValidationTest {

    @Test
    void saveTenantRejectsInvalidContactPhone() {
        TenantController controller = new TenantController(mock(JdbcTemplate.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.saveTenant(Map.of(
                "tenantName", "测试租户",
                "contactPhone", "789"
        )));

        assertEquals(400, exception.getCode());
        assertEquals("联系电话格式不正确", exception.getMessage());
    }

    @Test
    void saveTenantRejectsInvalidContactEmail() {
        TenantController controller = new TenantController(mock(JdbcTemplate.class));

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
        TenantController controller = new TenantController(mock(JdbcTemplate.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.saveTenantContact(1L, Map.of(
                "name", "张三",
                "phone", "11112312312312123123123"
        )));

        assertEquals(400, exception.getCode());
        assertEquals("联系电话格式不正确", exception.getMessage());
    }
}

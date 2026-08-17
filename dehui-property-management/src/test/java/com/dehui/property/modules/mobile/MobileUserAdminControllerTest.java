package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.mobile.controller.MobileUserAdminController;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.security.AuthPrincipal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class MobileUserAdminControllerTest {

    @Test
    void clearedPhoneVerificationIsReportedAsReverificationRequired() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> row = new HashMap<>();
        row.put("phone", "16650697751");
        row.put("phoneVerifiedAt", null);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("phone_verified_at AS phoneVerifiedAt")),
                eq(7L)
        )).thenReturn(List.of(row));

        assertEquals("REVERIFY_REQUIRED", new MobileIdentityService(jdbcTemplate).bindingState(7L, "PUBLIC"));
    }

    @Test
    void listMobileUsersReturnsTenantBindingInfo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> row = new HashMap<>();
        row.put("id", 7L);
        row.put("phone", "16650697751");
        row.put("userType", "TENANT");
        row.put("tenantId", 3L);
        row.put("tenantName", "德汇测试租户");
        row.put("tenantContactName", "张三");
        when(jdbcTemplate.queryForObject(argThat((String sql) -> sql != null && sql.contains("COUNT(*) FROM mobile_user")), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("FROM mobile_user mu")), any(Object[].class)))
                .thenReturn(List.of(row));

        ApiResponse<Map<String, Object>> response = newController(jdbcTemplate)
                .mobileUsers("166", "TENANT", "ENABLED", 0, 20);

        assertEquals(200, response.code());
        assertEquals(1L, response.data().get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) response.data().get("records");
        assertEquals(1, records.size());
        assertNull(records.getFirst().get("phone"));
        assertEquals("166****7751", records.getFirst().get("verifiedPhone"));
        assertEquals("德汇测试租户", records.getFirst().get("tenantName"));
        assertEquals("张三", records.getFirst().get("tenantContactName"));
    }

    @Test
    void arbitraryTenantIdBindingIsRejected() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> newController(jdbcTemplate)
                        .bindTenant(7L, Map.of("tenantId", 3L, "tenantContactId", 8L))
        );
        assertEquals(410, exception.getCode());
    }

    @Test
    void listMobileUsersBuildsValidKeywordFilterSql() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(List.of());

        newController(jdbcTemplate).mobileUsers("19900000001", null, null, 0, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Long.class), any(Object[].class));
        String countSql = sqlCaptor.getValue();
        assertTrue(countSql.contains("mu.deleted = 0 AND ("));
        assertFalse(countSql.contains("0AND"));
    }

    @Test
    void listMobileUsersAppliesWorkspaceFiltersAndWhitelistedOrdering() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class)))
                .thenReturn(List.of());

        newController(jdbcTemplate).mobileUsers(
                null,
                "TENANT",
                "ENABLED",
                "WX-",
                "166",
                "德汇",
                "张",
                "租户公司",
                "联系人",
                "工程师",
                null,
                null,
                "updatedAt:desc",
                "userType:asc",
                0,
                20
        );

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSqlCaptor.capture(), eq(Long.class), any(Object[].class));
        String countSql = countSqlCaptor.getValue();
        assertTrue(countSql.contains("mu.code LIKE ?"));
        assertTrue(countSql.contains("mu.phone LIKE ?"));
        assertTrue(countSql.contains("t.name LIKE ?"));
        assertTrue(countSql.contains("tc.name LIKE ?"));
        assertTrue(countSql.contains("su.real_name LIKE ?"));

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(listSqlCaptor.capture(), any(Object[].class));
        String listSql = listSqlCaptor.getValue();
        assertTrue(listSql.contains("ORDER BY mu.user_type ASC, mu.updated_at DESC, mu.id DESC"));
    }

    @Test
    void unbindTenantReturnsMobileUserToPublicIdentity() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("FROM mobile_user WHERE id")), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 7L)));
        ApiResponse<Void> response = newController(jdbcTemplate).unbindTenant(7L);

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null && sql.contains("current_bind_id = NULL") && sql.contains("user_type = 'PUBLIC'")),
                eq(0L),
                eq(7L)
        );
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null && sql.contains("phone_verified_at = NULL")),
                eq(0L),
                eq(7L)
        );
    }

    @Test
    void disablingMobileUserDelegatesStatusAndIdentityInvalidationToOneTransactionalService() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MobileIdentityService identityService = mock(MobileIdentityService.class);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_user WHERE id")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of("id", 7L)));
        AuthPrincipal administrator = new AuthPrincipal(
                1L,
                "admin",
                "SYSTEM",
                null,
                List.of("SUPER_ADMIN"),
                List.of("*")
        );
        MobileUserAdminController controller = new MobileUserAdminController(
                jdbcTemplate,
                null,
                null,
                identityService
        );

        ApiResponse<Void> response = controller.updateStatus(
                administrator,
                7L,
                Map.of("status", "DISABLED")
        );

        assertEquals(200, response.code());
        verify(identityService).adminUpdateStatus(7L, "DISABLED", 1L);
    }

    private MobileUserAdminController newController(JdbcTemplate jdbcTemplate) {
        return new MobileUserAdminController(jdbcTemplate);
    }
}

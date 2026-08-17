package com.dehui.property.modules.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class SystemAdminControllerTest {

    @Test
    void usersReturnAssignedRoleIdsCodesAndNamesForFrontendDisplayAndDialogPrefill() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", 3L);
        user.put("username", "test1");
        user.put("realName", "test1");
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("FROM sys_user user_record")
                        && sql.contains("user_record.deleted = 0")
        ))).thenReturn(List.of(user));
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("FROM sys_user_role ur")
                        && sql.contains("r.id AS roleId")
                        && sql.contains("r.code AS roleCode")
                        && sql.contains("r.name AS roleName")
        ), any(Object[].class))).thenReturn(List.of(Map.of(
                "userId", 3L,
                "roleId", 1L,
                "roleCode", "ADMIN",
                "roleName", "System Administrator"
        )));

        ApiResponse<List<Map<String, Object>>> response = newController(jdbcTemplate).users();

        assertEquals(List.of(1L), response.data().getFirst().get("roleIds"));
        assertEquals(List.of("ADMIN"), response.data().getFirst().get("roles"));
        assertEquals(List.of("System Administrator"), response.data().getFirst().get("roleNames"));
    }

    @Test
    void rolesReturnFrontendFieldAliases() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("code AS roleCode")
                        && sql.contains("name AS roleName")
                        && sql.contains("remark AS description")
                        && sql.contains("created_at AS createdTime")
                        && sql.contains("statusText")
        ))).thenReturn(List.of(Map.of(
                "id", 1L,
                "roleCode", "ADMIN",
                "roleName", "System Administrator",
                "description", "骞冲彴绠＄悊瑙掕壊",
                "statusText", "鍚敤"
        )));

        ApiResponse<List<Map<String, Object>>> response = newController(jdbcTemplate).roles();

        assertNotNull(response.data(), "roles API should use frontend aliases");
        assertEquals("ADMIN", response.data().getFirst().get("roleCode"));
        assertEquals("System Administrator", response.data().getFirst().get("roleName"));
        assertEquals("骞冲彴绠＄悊瑙掕壊", response.data().getFirst().get("description"));
        assertEquals("鍚敤", response.data().getFirst().get("statusText"));
    }

    @Test
    void menusReturnFrontendFieldAliases() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("code AS menuCode")
                        && sql.contains("name AS menuName")
        ))).thenReturn(List.of(Map.of(
                "id", 1L,
                "menuCode", "asset",
                "menuName", "璧勪骇绠＄悊"
        )));

        ApiResponse<List<Map<String, Object>>> response = newController(jdbcTemplate).menus();

        assertNotNull(response.data(), "menus API should use frontend aliases");
        assertEquals("asset", response.data().getFirst().get("menuCode"));
        assertEquals("璧勪骇绠＄悊", response.data().getFirst().get("menuName"));
    }

    @Test
    void menusReturnTreeForFrontendTreeComponent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("code AS menuCode")
                        && sql.contains("name AS menuName")
        ))).thenReturn(List.of(
                Map.of("id", 1L, "menuCode", "asset", "menuName", "璧勪骇绠＄悊"),
                Map.of("id", 2L, "menuCode", "asset:building", "menuName", "妤煎畤绠＄悊", "parentId", 1L)
        ));

        ApiResponse<List<Map<String, Object>>> response = newController(jdbcTemplate).menus();

        assertEquals(1, response.data().size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) response.data().getFirst().get("children");
        assertNotNull(children, "parent menu should contain children");
        assertEquals("妤煎畤绠＄悊", children.getFirst().get("menuName"));
    }

    @Test
    void permissionsReturnFrontendFieldAliases() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("code AS permissionCode")
                        && sql.contains("name AS permissionName")
        ))).thenReturn(List.of(Map.of(
                "id", 1L,
                "permissionCode", "system:role:view",
                "permissionName", "鏌ョ湅瑙掕壊"
        )));

        ApiResponse<List<Map<String, Object>>> response = newController(jdbcTemplate).permissions();

        assertNotNull(response.data(), "permissions API should use frontend aliases");
        assertEquals("system:role:view", response.data().getFirst().get("permissionCode"));
        assertEquals("鏌ョ湅瑙掕壊", response.data().getFirst().get("permissionName"));
    }

    @Test
    void createRoleAcceptsFrontendFieldAliases() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(
                eq("INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark) VALUES (?, ?, ?, ?, ?, ?, ?)"),
                eq("OPS"),
                eq("杩愯惀瑙掕壊"),
                eq("BUSINESS"),
                eq("ENABLED"),
                eq(0L),
                eq(0L),
                eq("杩愯惀浜哄憳")
        )).thenReturn(1);

        ApiResponse<Void> response = newController(jdbcTemplate).createRole(Map.of(
                "roleCode", "OPS",
                "roleName", "杩愯惀瑙掕壊",
                "description", "杩愯惀浜哄憳",
                "status", "ENABLED"
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                eq("INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark) VALUES (?, ?, ?, ?, ?, ?, ?)"),
                eq("OPS"),
                eq("杩愯惀瑙掕壊"),
                eq("BUSINESS"),
                eq("ENABLED"),
                eq(0L),
                eq(0L),
                eq("杩愯惀浜哄憳")
        );
    }

    @Test
    void updateRoleAcceptsFrontendDescriptionAlias() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        stubRoleForUpdate(jdbcTemplate, 7L, "OPS", "BUSINESS", "ENABLED");
        when(jdbcTemplate.update(
                eq("UPDATE sys_role SET name = ?, role_type = ?, status = ?, updated_by = ?, remark = ? WHERE id = ? AND deleted = 0"),
                eq("杩愯惀瑙掕壊"),
                eq("BUSINESS"),
                eq("ENABLED"),
                eq(0L),
                eq("杩愯惀浜哄憳"),
                eq(7L)
        )).thenReturn(1);

        ApiResponse<Void> response = newController(jdbcTemplate).updateRole(7L, Map.of(
                "roleName", "杩愯惀瑙掕壊",
                "description", "杩愯惀浜哄憳",
                "status", "ENABLED"
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                eq("UPDATE sys_role SET name = ?, role_type = ?, status = ?, updated_by = ?, remark = ? WHERE id = ? AND deleted = 0"),
                eq("杩愯惀瑙掕壊"),
                eq("BUSINESS"),
                eq("ENABLED"),
                eq(0L),
                eq("杩愯惀浜哄憳"),
                eq(7L)
        );
    }

    @Test
    void updateRoleRejectsDisablingAdminThroughEditEndpoint() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        stubRoleForUpdate(jdbcTemplate, 1L, "ADMIN", "SYSTEM", "ENABLED");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                newController(jdbcTemplate).updateRole(1L, Map.of(
                        "roleName", "管理员",
                        "roleType", "SYSTEM",
                        "status", "DISABLED"
                ))
        );

        assertEquals(400, exception.getCode());
        assertEquals("ADMIN和SUPER_ADMIN系统管理员角色不能禁用", exception.getMessage());
    }

    @Test
    void updateRoleStatusRejectsDisablingSuperAdmin() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        stubRoleForUpdate(jdbcTemplate, 2L, "SUPER_ADMIN", "SYSTEM", "ENABLED");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                newController(jdbcTemplate).updateRoleStatus(2L, "DISABLED", null)
        );

        assertEquals(400, exception.getCode());
        assertEquals("ADMIN和SUPER_ADMIN系统管理员角色不能禁用", exception.getMessage());
    }

    @Test
    void deleteRoleRejectsAdminAndSuperAdmin() {
        for (String roleCode : List.of("ADMIN", "SUPER_ADMIN")) {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            stubRoleForUpdate(jdbcTemplate, 1L, roleCode, "SYSTEM", "ENABLED");

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    newController(jdbcTemplate).deleteRole(1L)
            );

            assertEquals(400, exception.getCode());
            assertEquals("ADMIN和SUPER_ADMIN系统角色不能删除", exception.getMessage());
        }
    }

    @Test
    void updateRoleStatusRejectsDisablingLastEnabledSystemRole() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        stubRoleForUpdate(jdbcTemplate, 7L, "PLATFORM_OPERATOR", "SYSTEM", "ENABLED");
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("role_type = 'SYSTEM'") && sql.contains("FOR UPDATE")
        ), eq(Long.class))).thenReturn(List.of(7L));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                newController(jdbcTemplate).updateRoleStatus(7L, "DISABLED", null)
        );

        assertEquals(400, exception.getCode());
        assertEquals("必须至少保留一个启用的系统管理员角色", exception.getMessage());
    }

    @Test
    void updateRoleStatusAllowsDisablingNonLastCustomSystemRole() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        stubRoleForUpdate(jdbcTemplate, 7L, "PLATFORM_OPERATOR", "SYSTEM", "ENABLED");
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("role_type = 'SYSTEM'") && sql.contains("FOR UPDATE")
        ), eq(Long.class))).thenReturn(List.of(1L, 7L));
        when(jdbcTemplate.update(
                "UPDATE sys_role SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                "DISABLED",
                0L,
                7L
        )).thenReturn(1);

        ApiResponse<Void> response = newController(jdbcTemplate).updateRoleStatus(7L, "DISABLED", null);

        assertEquals(200, response.code());
    }

    @Test
    void createUserAcceptsRoleIdsFromCreateForm() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(jdbcTemplate.queryForList(
                "SELECT id FROM sys_role WHERE deleted = 0 AND status = 'ENABLED'",
                Long.class
        )).thenReturn(List.of(4L));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(Map.of("GENERATED_KEY", (Object) 12L));
            return 1;
        });

        ApiResponse<Void> response = newController(jdbcTemplate, passwordEncoder).createUser(Map.of(
                "username", "new-engineer",
                "realName", "New Engineer",
                "password", "123456",
                "status", "ENABLED",
                "roleIds", List.of(4L)
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                "INSERT INTO sys_user_role (user_id, role_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                12L,
                4L,
                0L,
                0L
        );
    }

    @Test
    void updateUserAcceptsRoleIdsFromEditForm() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM sys_user") && sql.contains("FOR UPDATE")),
                eq(8L)
        )).thenReturn(List.of(Map.of(
                "id", 8L,
                "username", "engineer01",
                "status", "ENABLED",
                "mobileEnabled", 0,
                "webLoginEnabled", 1
        )));
        when(jdbcTemplate.queryForList(
                "SELECT id FROM sys_role WHERE deleted = 0 AND status = 'ENABLED'",
                Long.class
        )).thenReturn(List.of(2L));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(jdbcTemplate.update(
                argThat(sql -> sql.contains("UPDATE sys_user") && sql.contains("mobile_enabled")),
                any(Object[].class)
        )).thenReturn(1);

        ApiResponse<Void> response = newController(jdbcTemplate).updateUser(8L, Map.of(
                "username", "engineer01",
                "realName", "宸ョ▼鍛樺伐",
                "status", "ENABLED",
                "roleIds", List.of(2L, 4L)
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                "INSERT INTO sys_user_role (user_id, role_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                8L,
                2L,
                0L,
                0L
        );
        verify(jdbcTemplate, never()).update(
                "INSERT INTO sys_user_role (user_id, role_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                8L,
                4L,
                0L,
                0L
        );
    }

    @Test
    void saveRoleMenusDeletesOldRelationsBeforeInsertingNewOnes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT id FROM sys_menu WHERE deleted = 0 AND status = 'ENABLED'",
                Long.class
        )).thenReturn(List.of(1L, 2L));

        ApiResponse<Void> response = newController(jdbcTemplate).saveRoleMenus(7L, Map.of("ids", List.of(1L, 2L)));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("DELETE FROM sys_role_menu WHERE role_id = ?", 7L);
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                1L,
                0L,
                0L
        );
    }

    @Test
    void saveRolePermissionsDeletesOldRelationsBeforeInsertingNewOnes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT id FROM sys_permission WHERE deleted = 0 AND status = 'ENABLED'",
                Long.class
        )).thenReturn(List.of(11L, 12L));

        ApiResponse<Void> response = newController(jdbcTemplate).saveRolePermissions(7L, Map.of("ids", List.of(11L, 12L)));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("DELETE FROM sys_role_permission WHERE role_id = ?", 7L);
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                11L,
                0L,
                0L
        );
    }

    @Test
    void legacyPermissionSaveRejectsUnknownIdsBeforeReplacingRelations() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT id FROM sys_permission WHERE deleted = 0 AND status = 'ENABLED'",
                Long.class
        )).thenReturn(List.of(11L));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                newController(jdbcTemplate).saveRolePermissions(7L, Map.of("permissionIds", List.of(999L)))
        );

        assertEquals(400, exception.getCode());
        verify(jdbcTemplate, never()).update("DELETE FROM sys_role_permission WHERE role_id = ?", 7L);
    }

    @Test
    void saveRoleAccessSynchronizesMenusAndPermissionsInOneRequest() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? AND deleted = 0 AND status = 'ENABLED'",
                Long.class,
                7L
        )).thenReturn(List.of(2L));
        when(jdbcTemplate.queryForList(
                "SELECT permission_id FROM sys_role_permission WHERE role_id = ? AND deleted = 0 AND status = 'ENABLED'",
                Long.class,
                7L
        )).thenReturn(List.of(11L));
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null && sql.contains("SELECT m.code") && sql.contains("FROM sys_role_menu")
        ), eq(String.class), eq(7L))).thenReturn(List.of("operation:workorders"));
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null && sql.contains("SELECT p.code") && sql.contains("FROM sys_role_permission")
        ), eq(String.class), eq(7L))).thenReturn(List.of("workorder:view"));
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null
                        && sql.contains("FROM sys_permission")
                        && sql.contains("code")
                        && sql.contains("deleted = 0")
        ))).thenReturn(List.of(
                Map.of("id", 11L, "code", "workorder:view"),
                Map.of("id", 12L, "code", "workorder:assign")
        ));
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null
                        && sql.contains("FROM sys_menu")
                        && sql.contains("parent_id AS parentId")
                        && sql.contains("path")
                        && sql.contains("deleted = 0")
        ))).thenReturn(List.of(menuRow(1L, null, ""), menuRow(2L, 1L, "/workorders")));

        ApiResponse<Map<String, Object>> access = newController(jdbcTemplate).roleAccess(7L);

        assertEquals(List.of(2L), access.data().get("menuIds"));
        assertEquals(List.of(11L), access.data().get("permissionIds"));
        assertEquals(List.of("operation:workorders"), access.data().get("menuCodes"));
        assertEquals(List.of("workorder:view"), access.data().get("permissionCodes"));
        assertEquals(1, access.data().get("menuCount"));
        assertEquals(1, access.data().get("permissionCount"));

        ApiResponse<Void> response = newController(jdbcTemplate).saveRoleAccess(7L, Map.of(
                "menuIds", List.of(1L, 2L),
                "permissionIds", List.of(11L, 12L)
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("DELETE FROM sys_role_menu WHERE role_id = ?", 7L);
        verify(jdbcTemplate).update("DELETE FROM sys_role_permission WHERE role_id = ?", 7L);
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                1L,
                0L,
                0L
        );
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                11L,
                0L,
                0L
        );
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                12L,
                0L,
                0L
        );
    }

    @Test
    void saveRoleAccessCompletesViewPermissionAndMenuAncestryFromOperationPermission() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null
                        && sql.contains("FROM sys_permission")
                        && sql.contains("code")
                        && sql.contains("deleted = 0")
        ))).thenReturn(List.of(
                Map.of("id", 11L, "code", "workorder:view"),
                Map.of("id", 12L, "code", "workorder:assign")
        ));
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null
                        && sql.contains("FROM sys_menu")
                        && sql.contains("parent_id AS parentId")
                        && sql.contains("path")
                        && sql.contains("deleted = 0")
        ))).thenReturn(List.of(menuRow(1L, null, ""), menuRow(2L, 1L, "/workorders")));

        ApiResponse<Void> response = newController(jdbcTemplate).saveRoleAccess(7L, Map.of(
                "permissionIds", List.of(12L)
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("DELETE FROM sys_role_menu WHERE role_id = ?", 7L);
        verify(jdbcTemplate).update("DELETE FROM sys_role_permission WHERE role_id = ?", 7L);
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                1L,
                0L,
                0L
        );
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                2L,
                0L,
                0L
        );
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                11L,
                0L,
                0L
        );
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                12L,
                0L,
                0L
        );
    }

    @Test
    void saveRoleAccessMapsFinanceDashboardMenuToDedicatedDashboardPermission() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null
                        && sql.contains("FROM sys_permission")
                        && sql.contains("code")
                        && sql.contains("deleted = 0")
        ))).thenReturn(List.of(
                Map.of("id", 11L, "code", "finance:bill:view"),
                Map.of("id", 12L, "code", "finance:dashboard:view")
        ));
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null
                        && sql.contains("FROM sys_menu")
                        && sql.contains("parent_id AS parentId")
                        && sql.contains("path")
                        && sql.contains("deleted = 0")
        ))).thenReturn(List.of(
                menuRow(1L, null, ""),
                menuRow(2L, 1L, "/finance/dashboard")
        ));

        ApiResponse<Void> response = newController(jdbcTemplate).saveRoleAccess(7L, Map.of(
                "menuIds", List.of(2L)
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                12L,
                0L,
                0L
        );
        verify(jdbcTemplate, never()).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                11L,
                0L,
                0L
        );
    }

    @Test
    void saveRoleAccessRejectsUnknownMenuAndPermissionIdsBeforeReplacingRelations() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null
                        && sql.contains("FROM sys_permission")
                        && sql.contains("code")
                        && sql.contains("deleted = 0")
        ))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null
                        && sql.contains("FROM sys_menu")
                        && sql.contains("parent_id AS parentId")
                        && sql.contains("path")
                        && sql.contains("deleted = 0")
        ))).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> newController(jdbcTemplate).saveRoleAccess(7L, Map.of(
                "menuIds", List.of(999L),
                "permissionIds", List.of(888L)
        )));

        assertEquals(400, exception.getCode());
        verify(jdbcTemplate, never()).update("DELETE FROM sys_role_menu WHERE role_id = ?", 7L);
        verify(jdbcTemplate, never()).update("DELETE FROM sys_role_permission WHERE role_id = ?", 7L);
    }

    @Test
    void saveRoleAccessRejectsMissingSelectionBeforeReplacingRelations() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                newController(jdbcTemplate).saveRoleAccess(7L, Map.of("unexpected", List.of(11L)))
        );

        assertEquals(400, exception.getCode());
        verify(jdbcTemplate, never()).update("DELETE FROM sys_role_menu WHERE role_id = ?", 7L);
        verify(jdbcTemplate, never()).update("DELETE FROM sys_role_permission WHERE role_id = ?", 7L);
    }

    @Test
    void saveRoleAccessRejectsAnyMalformedSelectionBeforeReplacingRelations() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                newController(jdbcTemplate).saveRoleAccess(7L, Map.of(
                        "menuIds", List.of(1L),
                        "permissionIds", "11"
                ))
        );

        assertEquals(400, exception.getCode());
        verify(jdbcTemplate, never()).update("DELETE FROM sys_role_menu WHERE role_id = ?", 7L);
        verify(jdbcTemplate, never()).update("DELETE FROM sys_role_permission WHERE role_id = ?", 7L);
    }

    @Test
    void saveRoleAccessUsesStablePermissionCodesWhenIdsAreOutdated() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null && sql.contains("FROM sys_permission") && sql.contains("ORDER BY id ASC")
        ))).thenReturn(List.of(
                Map.of("id", 21L, "code", "workorder:view"),
                Map.of("id", 22L, "code", "workorder:assign")
        ));
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql != null && sql.contains("FROM sys_menu") && sql.contains("parent_id AS parentId")
        ))).thenReturn(List.of(menuRow(1L, null, ""), menuRow(2L, 1L, "/workorders")));

        ApiResponse<Void> response = newController(jdbcTemplate).saveRoleAccess(7L, Map.of(
                "permissionIds", List.of(11L, 12L),
                "permissionCodes", List.of("workorder:assign")
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                21L,
                0L,
                0L
        );
        verify(jdbcTemplate).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                22L,
                0L,
                0L
        );
        verify(jdbcTemplate, never()).update(
                "INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                7L,
                11L,
                0L,
                0L
        );
    }

    private SystemAdminController newController(JdbcTemplate jdbcTemplate) {
        return newController(jdbcTemplate, mock(PasswordEncoder.class));
    }

    private void stubRoleForUpdate(
            JdbcTemplate jdbcTemplate,
            Long id,
            String roleCode,
            String roleType,
            String status) {
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("FROM sys_role")
                        && sql.contains("role_type AS roleType")
                        && sql.contains("FOR UPDATE")
        ), eq(id))).thenReturn(List.of(Map.of(
                "id", id,
                "code", roleCode,
                "roleType", roleType,
                "status", status
        )));
    }

    private Map<String, Object> menuRow(Long id, Long parentId, String path) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("parentId", parentId);
        row.put("path", path);
        return row;
    }

    private SystemAdminController newController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        return new SystemAdminController(jdbcTemplate, passwordEncoder);
    }
}

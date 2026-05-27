package com.dehui.property.modules.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class SystemAdminControllerTest {

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
                "roleName", "系统管理员",
                "description", "平台管理角色",
                "statusText", "启用"
        )));

        ApiResponse<List<Map<String, Object>>> response = newController(jdbcTemplate).roles();

        assertNotNull(response.data(), "roles API should use frontend aliases");
        assertEquals("ADMIN", response.data().getFirst().get("roleCode"));
        assertEquals("系统管理员", response.data().getFirst().get("roleName"));
        assertEquals("平台管理角色", response.data().getFirst().get("description"));
        assertEquals("启用", response.data().getFirst().get("statusText"));
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
                "menuName", "资产管理"
        )));

        ApiResponse<List<Map<String, Object>>> response = newController(jdbcTemplate).menus();

        assertNotNull(response.data(), "menus API should use frontend aliases");
        assertEquals("asset", response.data().getFirst().get("menuCode"));
        assertEquals("资产管理", response.data().getFirst().get("menuName"));
    }

    @Test
    void menusReturnTreeForFrontendTreeComponent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                sql.contains("code AS menuCode")
                        && sql.contains("name AS menuName")
        ))).thenReturn(List.of(
                Map.of("id", 1L, "menuCode", "asset", "menuName", "资产管理"),
                Map.of("id", 2L, "menuCode", "asset:building", "menuName", "楼宇管理", "parentId", 1L)
        ));

        ApiResponse<List<Map<String, Object>>> response = newController(jdbcTemplate).menus();

        assertEquals(1, response.data().size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) response.data().getFirst().get("children");
        assertNotNull(children, "parent menu should contain children");
        assertEquals("楼宇管理", children.getFirst().get("menuName"));
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
                "permissionName", "查看角色"
        )));

        ApiResponse<List<Map<String, Object>>> response = newController(jdbcTemplate).permissions();

        assertNotNull(response.data(), "permissions API should use frontend aliases");
        assertEquals("system:role:view", response.data().getFirst().get("permissionCode"));
        assertEquals("查看角色", response.data().getFirst().get("permissionName"));
    }

    @Test
    void createRoleAcceptsFrontendFieldAliases() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(
                eq("INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark) VALUES (?, ?, ?, ?, ?, ?, ?)"),
                eq("OPS"),
                eq("运营角色"),
                eq("BUSINESS"),
                eq("ENABLED"),
                eq(0L),
                eq(0L),
                eq("运营人员")
        )).thenReturn(1);

        ApiResponse<Void> response = newController(jdbcTemplate).createRole(Map.of(
                "roleCode", "OPS",
                "roleName", "运营角色",
                "description", "运营人员",
                "status", "ENABLED"
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                eq("INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark) VALUES (?, ?, ?, ?, ?, ?, ?)"),
                eq("OPS"),
                eq("运营角色"),
                eq("BUSINESS"),
                eq("ENABLED"),
                eq(0L),
                eq(0L),
                eq("运营人员")
        );
    }

    @Test
    void updateRoleAcceptsFrontendDescriptionAlias() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(
                eq("UPDATE sys_role SET name = ?, role_type = ?, status = ?, updated_by = ?, remark = ? WHERE id = ? AND deleted = 0"),
                eq("运营角色"),
                eq("BUSINESS"),
                eq("ENABLED"),
                eq(0L),
                eq("运营人员"),
                eq(7L)
        )).thenReturn(1);

        ApiResponse<Void> response = newController(jdbcTemplate).updateRole(7L, Map.of(
                "roleName", "运营角色",
                "description", "运营人员",
                "status", "ENABLED"
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                eq("UPDATE sys_role SET name = ?, role_type = ?, status = ?, updated_by = ?, remark = ? WHERE id = ? AND deleted = 0"),
                eq("运营角色"),
                eq("BUSINESS"),
                eq("ENABLED"),
                eq(0L),
                eq("运营人员"),
                eq(7L)
        );
    }

    @Test
    void saveRoleMenusDeletesOldRelationsBeforeInsertingNewOnes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

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

    private SystemAdminController newController(JdbcTemplate jdbcTemplate) {
        return new SystemAdminController(jdbcTemplate, mock(PasswordEncoder.class));
    }
}

package com.dehui.property.modules.system.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SystemAdminController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @PatchMapping("/system/me/password")
    public ApiResponse<Void> changePassword(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        String password = JdbcMaps.requiredStr(body, "Password is required", "newPassword", "password");
        jdbcTemplate.update("UPDATE sys_user SET password_hash = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                passwordEncoder.encode(password), principal.userId(), principal.userId());
        return ApiResponse.success();
    }

    @GetMapping("/system/users")
    public ApiResponse<List<Map<String, Object>>> users() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT id, code, username, real_name AS realName, phone, email, status, remark FROM sys_user WHERE deleted = 0 ORDER BY id DESC"
        ));
    }

    @PostMapping("/system/users")
    public ApiResponse<Void> createUser(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO sys_user (code, username, password_hash, real_name, phone, email, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                JdbcMaps.code("USR"),
                JdbcMaps.requiredStr(body, "Username is required", "username"),
                passwordEncoder.encode(JdbcMaps.strOr(body, "123456", "password")),
                JdbcMaps.strOr(body, JdbcMaps.requiredStr(body, "Username is required", "username"), "realName", "real_name", "name"),
                JdbcMaps.str(body, "phone"),
                JdbcMaps.str(body, "email"),
                JdbcMaps.strOr(body, "ENABLED", "status"),
                0L,
                0L,
                JdbcMaps.str(body, "remark")
        );
        return ApiResponse.success();
    }

    @PutMapping("/system/users/{id}")
    public ApiResponse<Void> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int updated = jdbcTemplate.update(
                """
                UPDATE sys_user SET real_name = ?, phone = ?, email = ?, status = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                JdbcMaps.strOr(body, JdbcMaps.str(body, "username"), "realName", "real_name", "name"),
                JdbcMaps.str(body, "phone"),
                JdbcMaps.str(body, "email"),
                JdbcMaps.strOr(body, "ENABLED", "status"),
                0L,
                JdbcMaps.str(body, "remark"),
                id
        );
        ensureUpdated(updated, "User not found");
        return ApiResponse.success();
    }

    @PostMapping("/system/users/{id}/roles")
    public ApiResponse<Void> saveUserRoles(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update("UPDATE sys_user_role SET deleted = 1 WHERE user_id = ?", id);
        for (Long roleId : ids(body)) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                    id, roleId, 0L, 0L);
        }
        return ApiResponse.success();
    }

    @PatchMapping("/system/users/{id}/status")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, Object> body) {
        ensureUpdated(jdbcTemplate.update("UPDATE sys_user SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                statusValue(status, body), 0L, id), "User not found");
        return ApiResponse.success();
    }

    @PatchMapping("/system/users/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE sys_user SET password_hash = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                passwordEncoder.encode("123456"), 0L, id), "User not found");
        return ApiResponse.success(Map.of("password", "123456"));
    }

    @DeleteMapping("/system/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE sys_user SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "User not found");
        return ApiResponse.success();
    }

    @GetMapping("/system/roles")
    public ApiResponse<List<Map<String, Object>>> roles() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, code AS roleCode, name, name AS roleName, role_type AS roleType, status,
                       CASE status WHEN 'DISABLED' THEN '禁用' ELSE '启用' END AS statusText,
                       remark, remark AS description, created_at AS createdTime, updated_at AS updatedTime
                FROM sys_role WHERE deleted = 0 ORDER BY id DESC
                """
        ));
    }

    @PostMapping("/system/roles")
    public ApiResponse<Void> createRole(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                "INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark) VALUES (?, ?, ?, ?, ?, ?, ?)",
                JdbcMaps.strOr(body, JdbcMaps.code("ROLE"), "code", "roleCode"),
                JdbcMaps.requiredStr(body, "Role name is required", "name", "roleName"),
                JdbcMaps.strOr(body, "BUSINESS", "roleType"),
                JdbcMaps.strOr(body, "ENABLED", "status"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "description")
        );
        return ApiResponse.success();
    }

    @PutMapping("/system/roles/{id}")
    public ApiResponse<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ensureUpdated(jdbcTemplate.update(
                "UPDATE sys_role SET name = ?, role_type = ?, status = ?, updated_by = ?, remark = ? WHERE id = ? AND deleted = 0",
                JdbcMaps.requiredStr(body, "Role name is required", "name", "roleName"),
                JdbcMaps.strOr(body, "BUSINESS", "roleType"),
                JdbcMaps.strOr(body, "ENABLED", "status"),
                0L,
                JdbcMaps.str(body, "remark", "description"),
                id
        ), "Role not found");
        return ApiResponse.success();
    }

    @PatchMapping("/system/roles/{id}/status")
    public ApiResponse<Void> updateRoleStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, Object> body) {
        ensureUpdated(jdbcTemplate.update("UPDATE sys_role SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                statusValue(status, body), 0L, id), "Role not found");
        return ApiResponse.success();
    }

    @DeleteMapping("/system/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE sys_role SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Role not found");
        return ApiResponse.success();
    }

    @GetMapping("/system/menus")
    public ApiResponse<List<Map<String, Object>>> menus() {
        return ApiResponse.success(menuTree(jdbcTemplate.queryForList(
                """
                SELECT id, code, code AS menuCode, parent_id AS parentId, name, name AS menuName,
                       path, component, icon, sort_order AS sortOrder, status
                FROM sys_menu WHERE deleted = 0 ORDER BY sort_order ASC, id ASC
                """
        )));
    }

    @GetMapping("/system/permissions")
    public ApiResponse<List<Map<String, Object>>> permissions() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, code AS permissionCode, name, name AS permissionName, module, action, status
                FROM sys_permission WHERE deleted = 0 ORDER BY module ASC, id ASC
                """
        ));
    }

    @GetMapping("/system/roles/{id}/menus")
    public ApiResponse<List<Long>> roleMenus(@PathVariable Long id) {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id = ? AND deleted = 0", Long.class, id));
    }

    @PostMapping("/system/roles/{id}/menus")
    public ApiResponse<Void> saveRoleMenus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", id);
        for (Long menuId : ids(body)) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                    id, menuId, 0L, 0L);
        }
        return ApiResponse.success();
    }

    @GetMapping("/system/roles/{id}/permissions")
    public ApiResponse<List<Long>> rolePermissions(@PathVariable Long id) {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT permission_id FROM sys_role_permission WHERE role_id = ? AND deleted = 0", Long.class, id));
    }

    @PostMapping("/system/roles/{id}/permissions")
    public ApiResponse<Void> saveRolePermissions(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", id);
        for (Long permissionId : ids(body)) {
            jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                    id, permissionId, 0L, 0L);
        }
        return ApiResponse.success();
    }

    private List<Long> ids(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("ids");
        List<Long> ids = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Number number) {
                    ids.add(number.longValue());
                } else if (item != null && !String.valueOf(item).isBlank()) {
                    ids.add(Long.valueOf(String.valueOf(item)));
                }
            }
        }
        return ids;
    }

    private List<Map<String, Object>> menuTree(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> node = new LinkedHashMap<>(row);
            node.put("children", new ArrayList<Map<String, Object>>());
            byId.put(((Number) node.get("id")).longValue(), node);
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : byId.values()) {
            Object parentId = node.get("parentId");
            if (parentId instanceof Number number && byId.containsKey(number.longValue())) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) byId.get(number.longValue()).get("children");
                children.add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }

    private String statusValue(String requestStatus, Map<String, Object> body) {
        if (requestStatus != null && !requestStatus.isBlank()) {
            return requestStatus.trim();
        }
        return JdbcMaps.strOr(body, "ENABLED", "status");
    }
}

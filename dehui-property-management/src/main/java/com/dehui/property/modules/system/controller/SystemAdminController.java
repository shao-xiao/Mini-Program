package com.dehui.property.modules.system.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
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
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SystemAdminController {

    private static final Set<String> CORE_ADMIN_ROLE_CODES = Set.of("ADMIN", "SUPER_ADMIN");

    private static final Map<String, String> PATH_VIEW_PERMISSION_CODE = Map.ofEntries(
            Map.entry("/dashboard", "dashboard:view"),
            Map.entry("/buildings", "building:view"),
            Map.entry("/floors", "floor:view"),
            Map.entry("/rooms", "room:view"),
            Map.entry("/equipment", "asset:view"),
            Map.entry("/investment/contents", "investment-content:view"),
            Map.entry("/investment/listings", "investment-listing:view"),
            Map.entry("/investment/rooms", "investment-listing:view"),
            Map.entry("/investment/leads", "investment-lead:view"),
            Map.entry("/contracts", "contract:view"),
            Map.entry("/tenants", "tenant:view"),
            Map.entry("/leases", "lease:view"),
            Map.entry("/workorders", "workorder:view"),
            Map.entry("/inspections", "inspection:view"),
            Map.entry("/visitors", "visitor:view"),
            Map.entry("/announcements", "announcement:view"),
            Map.entry("/parking/spaces", "parking:view"),
            Map.entry("/parking/bills", "parking-bill:view"),
            Map.entry("/meetings/rooms", "meeting-room:view"),
            Map.entry("/meetings/bookings", "meeting-booking:view"),
            Map.entry("/energy/meters", "energy:meter:view"),
            Map.entry("/energy/records", "energy:view"),
            Map.entry("/energy/stats", "energy:view"),
            Map.entry("/bills", "bill:view"),
            Map.entry("/finance/dashboard", "finance:dashboard:view"),
            Map.entry("/ai/daily-report", "ai:view"),
            Map.entry("/system/users", "system:user:view"),
            Map.entry("/system/mobile-users", "mobile:view"),
            Map.entry("/system/roles", "system:role:view")
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final MobileIdentityService mobileIdentityService;

    public SystemAdminController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this(jdbcTemplate, passwordEncoder, new MobileIdentityService(jdbcTemplate));
    }

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
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                """
                SELECT user_record.id,
                       user_record.code,
                       user_record.username,
                       user_record.real_name AS realName,
                       user_record.phone,
                       user_record.email,
                       user_record.status,
                       user_record.mobile_enabled AS mobileEnabled,
                       user_record.web_login_enabled AS webLoginEnabled,
                       user_record.primary_role_id AS primaryRoleId,
                       user_record.last_login_at AS lastLoginAt,
                       CASE WHEN EXISTS (
                         SELECT 1
                         FROM mobile_user_bind bind_record
                         WHERE bind_record.sys_user_id = user_record.id
                           AND bind_record.bind_type = 'INTERNAL'
                           AND bind_record.status = 'ENABLED'
                           AND bind_record.deleted = 0
                       ) THEN 1 ELSE 0 END AS mobileBound,
                       user_record.remark
                FROM sys_user user_record
                WHERE user_record.deleted = 0
                ORDER BY user_record.id DESC
                """
        );
        attachUserRoles(users);
        return ApiResponse.success(users);
    }

    @PostMapping("/system/users")
    @Transactional
    public ApiResponse<Void> createUser(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        boolean webLoginEnabled = Boolean.TRUE.equals(JdbcMaps.bool(body, true, "webLoginEnabled"));
        boolean mobileEnabled = Boolean.TRUE.equals(JdbcMaps.bool(body, false, "mobileEnabled"));
        String realName = JdbcMaps.requiredStr(body, "姓名不能为空", "realName", "real_name", "name");
        String phone = normalizedPhone(JdbcMaps.str(body, "phone"));
        String status = JdbcMaps.strOr(body, "ENABLED", "status").trim().toUpperCase(Locale.ROOT);
        List<Long> roleIds = requestedUserRoleIds(body);
        validateUserSettings(null, phone, status, webLoginEnabled, mobileEnabled, roleIds);
        validateUserRoleGrantScope(principal, null, roleIds);
        String username = webLoginEnabled
                ? JdbcMaps.requiredStr(body, "开启后台登录时用户名不能为空", "username")
                : generatedMobileUsername();
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ? AND deleted = 0",
                Integer.class,
                username
        );
        if (existing != null && existing > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        String rawPassword = webLoginEnabled
                ? JdbcMaps.requiredStr(body, "开启后台登录时初始密码不能为空", "password")
                : UUID.randomUUID().toString();
        Long primaryRoleId = primaryRoleId(body, roleIds);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO sys_user
                      (code, username, password_hash, real_name, phone, email, status,
                       mobile_enabled, web_login_enabled, primary_role_id,
                       created_by, updated_by, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, JdbcMaps.code("USR"));
            statement.setString(2, username);
            statement.setString(3, passwordEncoder.encode(rawPassword));
            statement.setString(4, realName);
            statement.setString(5, phone);
            statement.setString(6, JdbcMaps.str(body, "email"));
            statement.setString(7, status);
            statement.setBoolean(8, mobileEnabled);
            statement.setBoolean(9, webLoginEnabled);
            if (primaryRoleId == null) statement.setObject(10, null);
            else statement.setLong(10, primaryRoleId);
            statement.setLong(11, principal.userId());
            statement.setLong(12, principal.userId());
            statement.setString(13, JdbcMaps.str(body, "remark"));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            replaceUserRoles(key.longValue(), roleIds, principal.userId());
        }
        return ApiResponse.success();
    }

    @PutMapping("/system/users/{id}")
    @Transactional
    public ApiResponse<Void> updateUser(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        UserSnapshot existing = userForUpdate(id);
        boolean webLoginEnabled = Boolean.TRUE.equals(JdbcMaps.bool(body, existing.webLoginEnabled(), "webLoginEnabled"));
        boolean mobileEnabled = Boolean.TRUE.equals(JdbcMaps.bool(body, existing.mobileEnabled(), "mobileEnabled"));
        String status = JdbcMaps.strOr(body, existing.status(), "status").trim().toUpperCase(Locale.ROOT);
        String phone = normalizedPhone(JdbcMaps.str(body, "phone"));
        List<Long> roleIds = body != null && (body.containsKey("primaryRoleId") || body.containsKey("roleIds"))
                ? requestedUserRoleIds(body)
                : activeRoleIds(id);
        validateUserSettings(id, phone, status, webLoginEnabled, mobileEnabled, roleIds);
        validateUserRoleGrantScope(principal, id, roleIds);
        protectUserMutation(principal, existing, status, roleIds, false);
        String username = webLoginEnabled
                ? JdbcMaps.requiredStr(body, "开启后台登录时用户名不能为空", "username")
                : existing.username();
        ensureUsernameAvailable(username, id);
        Long primaryRoleId = primaryRoleId(body, roleIds);
        int updated = jdbcTemplate.update(
                """
                UPDATE sys_user
                SET username = ?, real_name = ?, phone = ?, email = ?, status = ?,
                    mobile_enabled = ?, web_login_enabled = ?, primary_role_id = ?,
                    updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                username,
                JdbcMaps.requiredStr(body, "姓名不能为空", "realName", "real_name", "name"),
                phone,
                JdbcMaps.str(body, "email"),
                status,
                mobileEnabled ? 1 : 0,
                webLoginEnabled ? 1 : 0,
                primaryRoleId,
                principal.userId(),
                JdbcMaps.str(body, "remark"),
                id
        );
        ensureUpdated(updated, "User not found");
        if (body != null && (body.containsKey("primaryRoleId") || body.containsKey("roleIds"))) {
            replaceUserRoles(id, roleIds, principal.userId());
        }
        mobileIdentityService.invalidateIneligibleStaffBindings(
                List.of(id),
                principal.userId(),
                "员工手机号、状态、移动开关或角色发生变化"
        );
        return ApiResponse.success();
    }

    @PostMapping("/system/users/{id}/roles")
    @Transactional
    public ApiResponse<Void> saveUserRoles(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        UserSnapshot existing = userForUpdate(id);
        List<Long> roleIds = requestedUserRoleIds(body);
        validateKnownIds(roleIds, enabledRoleIds(), "角色选择已失效，请刷新后重试");
        validateUserRoleGrantScope(principal, id, roleIds);
        protectUserMutation(principal, existing, existing.status(), roleIds, false);
        replaceUserRoles(id, roleIds, principal.userId());
        jdbcTemplate.update(
                "UPDATE sys_user SET primary_role_id = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                primaryRoleId(body, roleIds),
                principal.userId(),
                id
        );
        mobileIdentityService.invalidateIneligibleStaffBindings(
                List.of(id),
                principal.userId(),
                "员工角色发生变化"
        );
        return ApiResponse.success();
    }

    @PatchMapping("/system/users/{id}/status")
    @Transactional
    public ApiResponse<Void> updateUserStatus(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, Object> body) {
        UserSnapshot existing = userForUpdate(id);
        String nextStatus = statusValue(status, body).trim().toUpperCase(Locale.ROOT);
        protectUserMutation(principal, existing, nextStatus, activeRoleIds(id), false);
        ensureUpdated(jdbcTemplate.update("UPDATE sys_user SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                nextStatus, principal.userId(), id), "User not found");
        mobileIdentityService.invalidateIneligibleStaffBindings(
                List.of(id),
                principal.userId(),
                "员工状态发生变化"
        );
        return ApiResponse.success();
    }

    @PatchMapping("/system/users/{id}/reset-password")
    @Transactional
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable Long id) {
        UserSnapshot user = userForUpdate(id);
        if (!user.webLoginEnabled()) {
            throw new BusinessException(400, "该员工未开启管理后台登录，无需重置后台密码");
        }
        ensureUpdated(jdbcTemplate.update("UPDATE sys_user SET password_hash = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                passwordEncoder.encode("123456"), 0L, id), "User not found");
        return ApiResponse.success(Map.of("password", "123456"));
    }

    @DeleteMapping("/system/users/{id}")
    @Transactional
    public ApiResponse<Void> deleteUser(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        UserSnapshot existing = userForUpdate(id);
        protectUserMutation(principal, existing, "DISABLED", List.of(), true);
        ensureUpdated(jdbcTemplate.update(
                "UPDATE sys_user SET deleted = 1, status = 'DISABLED', mobile_enabled = 0, web_login_enabled = 0, updated_by = ? WHERE id = ? AND deleted = 0",
                principal.userId(),
                id
        ), "User not found");
        mobileIdentityService.invalidateIneligibleStaffBindings(
                List.of(id),
                principal.userId(),
                "员工已删除"
        );
        return ApiResponse.success();
    }

    @DeleteMapping("/system/users/{id}/mobile-binding")
    @Transactional
    public ApiResponse<Void> unbindUserMobile(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        userForUpdate(id);
        mobileIdentityService.invalidateAllStaffBindings(
                id,
                principal.userId(),
                "管理员解除物业员工移动绑定"
        );
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
    public ApiResponse<Void> createRole(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        String roleCode = JdbcMaps.strOr(body, JdbcMaps.code("ROLE"), "code", "roleCode")
                .trim()
                .toUpperCase(Locale.ROOT);
        String roleType = JdbcMaps.strOr(body, "BUSINESS", "roleType").trim().toUpperCase(Locale.ROOT);
        requireSuperForCoreRoleDefinition(principal, roleCode, roleType);
        jdbcTemplate.update(
                "INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark) VALUES (?, ?, ?, ?, ?, ?, ?)",
                roleCode,
                JdbcMaps.requiredStr(body, "Role name is required", "roleName", "name"),
                roleType,
                JdbcMaps.strOr(body, "ENABLED", "status"),
                principal.userId(),
                principal.userId(),
                JdbcMaps.str(body, "description", "remark")
        );
        return ApiResponse.success();
    }

    @PutMapping("/system/roles/{id}")
    @Transactional
    public ApiResponse<Void> updateRole(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        RoleSnapshot role = roleForUpdate(id);
        requireSuperForCoreRoleMutation(principal, role);
        String nextStatus = JdbcMaps.strOr(body, "ENABLED", "status");
        ensureRoleStatusCanChange(role, nextStatus);
        String nextRoleType = JdbcMaps.strOr(body, role.roleType(), "roleType").trim().toUpperCase(Locale.ROOT);
        if (isCoreAdminRole(role.code()) && !"SYSTEM".equals(nextRoleType)) {
            throw new BusinessException(400, "系统管理员角色不能改为普通业务角色");
        }
        ensureUpdated(jdbcTemplate.update(
                "UPDATE sys_role SET name = ?, role_type = ?, status = ?, updated_by = ?, remark = ? WHERE id = ? AND deleted = 0",
                JdbcMaps.requiredStr(body, "Role name is required", "roleName", "name"),
                nextRoleType,
                nextStatus,
                principal.userId(),
                JdbcMaps.str(body, "description", "remark"),
                id
        ), "Role not found");
        invalidateRoleStaffBindings(id, principal.userId(), "员工角色状态或类型发生变化");
        return ApiResponse.success();
    }

    @PatchMapping("/system/roles/{id}/status")
    @Transactional
    public ApiResponse<Void> updateRoleStatus(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, Object> body) {
        RoleSnapshot role = roleForUpdate(id);
        requireSuperForCoreRoleMutation(principal, role);
        String nextStatus = statusValue(status, body);
        ensureRoleStatusCanChange(role, nextStatus);
        ensureUpdated(jdbcTemplate.update("UPDATE sys_role SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                nextStatus, principal.userId(), id), "Role not found");
        invalidateRoleStaffBindings(id, principal.userId(), "员工角色状态发生变化");
        return ApiResponse.success();
    }

    @DeleteMapping("/system/roles/{id}")
    @Transactional
    public ApiResponse<Void> deleteRole(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        RoleSnapshot role = roleForUpdate(id);
        requireSuperForCoreRoleMutation(principal, role);
        if (isCoreAdminRole(role.code())) {
            throw new BusinessException(400, "ADMIN和SUPER_ADMIN系统角色不能删除");
        }
        ensureUpdated(jdbcTemplate.update(
                "UPDATE sys_role SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                principal.userId(),
                id
        ), "Role not found");
        invalidateRoleStaffBindings(id, principal.userId(), "员工角色已删除");
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
    @Transactional
    public ApiResponse<Void> saveRoleMenus(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        List<Long> menuIds = requiredIds(body, "Menu selection is required", "ids", "menuIds", "menus");
        validateKnownIds(
                menuIds,
                jdbcTemplate.queryForList(
                        "SELECT id FROM sys_menu WHERE deleted = 0 AND status = 'ENABLED'",
                        Long.class
                ),
                "Menu selection is outdated; refresh and try again"
        );
        List<Long> currentPermissionIds = jdbcTemplate.queryForList(
                "SELECT permission_id FROM sys_role_permission WHERE role_id = ? AND deleted = 0 AND status = 'ENABLED'",
                Long.class,
                id
        );
        validateRoleGrantScope(principal, id, new AccessSelection(menuIds, currentPermissionIds));
        replaceRoleMenus(id, menuIds);
        return ApiResponse.success();
    }

    @GetMapping("/system/roles/{id}/permissions")
    public ApiResponse<List<Long>> rolePermissions(@PathVariable Long id) {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT permission_id FROM sys_role_permission WHERE role_id = ? AND deleted = 0", Long.class, id));
    }

    @PostMapping("/system/roles/{id}/permissions")
    @Transactional
    public ApiResponse<Void> saveRolePermissions(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        List<Long> permissionIds = requiredIds(
                body,
                "Permission selection is required",
                "ids",
                "permissionIds",
                "permissions"
        );
        validateKnownIds(
                permissionIds,
                jdbcTemplate.queryForList(
                        "SELECT id FROM sys_permission WHERE deleted = 0 AND status = 'ENABLED'",
                        Long.class
                ),
                "Permission selection is outdated; refresh and try again"
        );
        List<Long> currentMenuIds = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? AND deleted = 0 AND status = 'ENABLED'",
                Long.class,
                id
        );
        validateRoleGrantScope(principal, id, new AccessSelection(currentMenuIds, permissionIds));
        replaceRolePermissions(id, permissionIds);
        invalidateRoleStaffBindings(id, principal.userId(), "员工移动工作台权限发生变化");
        return ApiResponse.success();
    }

    @GetMapping("/system/roles/{id}/access")
    public ApiResponse<Map<String, Object>> roleAccess(@PathVariable Long id) {
        List<Long> menuIds = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? AND deleted = 0 AND status = 'ENABLED'",
                Long.class,
                id
        );
        List<Long> permissionIds = jdbcTemplate.queryForList(
                "SELECT permission_id FROM sys_role_permission WHERE role_id = ? AND deleted = 0 AND status = 'ENABLED'",
                Long.class,
                id
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("menuIds", menuIds);
        data.put("permissionIds", permissionIds);
        data.put("menuCodes", jdbcTemplate.queryForList(
                """
                SELECT m.code
                FROM sys_role_menu rm
                JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 'ENABLED'
                WHERE rm.role_id = ? AND rm.deleted = 0 AND rm.status = 'ENABLED'
                ORDER BY m.id ASC
                """,
                String.class,
                id
        ));
        data.put("permissionCodes", jdbcTemplate.queryForList(
                """
                SELECT p.code
                FROM sys_role_permission rp
                JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.status = 'ENABLED'
                WHERE rp.role_id = ? AND rp.deleted = 0 AND rp.status = 'ENABLED'
                ORDER BY p.id ASC
                """,
                String.class,
                id
        ));
        data.put("menuCount", menuIds.size());
        data.put("permissionCount", permissionIds.size());
        return ApiResponse.success(data);
    }

    @PostMapping("/system/roles/{id}/access")
    @Transactional
    public ApiResponse<Void> saveRoleAccess(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        requireAnySelectionKey(
                body,
                "Role access selection is required",
                "menuIds",
                "menus",
                "menuCodes",
                "permissionIds",
                "permissions",
                "permissionCodes",
                "ids"
        );
        AccessSelection selection = normalizeRoleAccess(
                ids(body, "menuIds", "menus"),
                strings(body, "menuCodes"),
                ids(body, "permissionIds", "permissions", "ids"),
                strings(body, "permissionCodes")
        );
        validateRoleGrantScope(principal, id, selection);
        replaceRoleMenus(id, selection.menuIds());
        replaceRolePermissions(id, selection.permissionIds());
        invalidateRoleStaffBindings(id, principal.userId(), "员工移动工作台权限发生变化");
        return ApiResponse.success();
    }

    private AccessSelection normalizeRoleAccess(
            List<Long> requestedMenuIds,
            List<String> requestedMenuCodes,
            List<Long> requestedPermissionIds,
            List<String> requestedPermissionCodes) {
        List<Map<String, Object>> permissionRows = rowsOrEmpty(jdbcTemplate.queryForList(
                "SELECT id, code FROM sys_permission WHERE deleted = 0 AND status = 'ENABLED' ORDER BY id ASC"
        ));
        Map<Long, String> permissionCodesById = new LinkedHashMap<>();
        Map<String, Long> permissionIdsByCode = new LinkedHashMap<>();
        for (Map<String, Object> row : permissionRows) {
            Long id = longValue(row.get("id"));
            String code = textValue(row.get("code"));
            if (id != null && code != null) {
                permissionCodesById.put(id, code);
                permissionIdsByCode.put(code, id);
            }
        }

        Set<Long> permissionIds = new LinkedHashSet<>();
        Set<String> permissionCodes = new LinkedHashSet<>();
        List<Long> unknownPermissionIds = new ArrayList<>();
        for (Long permissionId : requestedPermissionIds) {
            String code = permissionCodesById.get(permissionId);
            if (code != null) {
                permissionIds.add(permissionId);
                permissionCodes.add(code);
            } else {
                unknownPermissionIds.add(permissionId);
            }
        }
        for (String code : requestedPermissionCodes) {
            Long permissionId = permissionIdsByCode.get(code);
            if (permissionId == null) {
                throw new BusinessException(400, "Permission selection is outdated; refresh and try again");
            }
            permissionIds.add(permissionId);
            permissionCodes.add(code);
        }
        if (!unknownPermissionIds.isEmpty() && requestedPermissionCodes.isEmpty()) {
            throw new BusinessException(400, "Permission selection is outdated; refresh and try again");
        }

        for (String code : new ArrayList<>(permissionCodes)) {
            addPermissionCode(permissionIds, permissionCodes, permissionIdsByCode, viewPermissionCodeFor(code));
        }

        List<Map<String, Object>> menuRows = rowsOrEmpty(jdbcTemplate.queryForList(
                "SELECT id, code, parent_id AS parentId, path FROM sys_menu WHERE deleted = 0 AND status = 'ENABLED' ORDER BY sort_order ASC, id ASC"
        ));
        Map<Long, MenuAccessRow> menusById = new LinkedHashMap<>();
        Map<String, Long> menuIdsByCode = new LinkedHashMap<>();
        for (Map<String, Object> row : menuRows) {
            Long id = longValue(row.get("id"));
            if (id == null) {
                continue;
            }
            String code = textValue(row.get("code"));
            menusById.put(id, new MenuAccessRow(id, code, longValue(row.get("parentId")), textValue(row.get("path"))));
            if (code != null) {
                menuIdsByCode.put(code, id);
            }
        }

        Set<Long> menuIds = new LinkedHashSet<>();
        List<Long> unknownMenuIds = new ArrayList<>();
        for (Long menuId : requestedMenuIds) {
            if (menusById.containsKey(menuId)) {
                menuIds.add(menuId);
            } else {
                unknownMenuIds.add(menuId);
            }
        }
        for (String code : requestedMenuCodes) {
            Long menuId = menuIdsByCode.get(code);
            if (menuId == null) {
                throw new BusinessException(400, "Menu selection is outdated; refresh and try again");
            }
            menuIds.add(menuId);
        }
        if (!unknownMenuIds.isEmpty() && requestedMenuCodes.isEmpty()) {
            throw new BusinessException(400, "Menu selection is outdated; refresh and try again");
        }
        for (Long menuId : new ArrayList<>(menuIds)) {
            addMenuWithAncestors(menuIds, menusById, menuId);
            MenuAccessRow menu = menusById.get(menuId);
            if (menu != null) {
                addPermissionCode(permissionIds, permissionCodes, permissionIdsByCode, viewPermissionCodeForPath(menu.path()));
            }
        }

        for (MenuAccessRow menu : menusById.values()) {
            String viewCode = viewPermissionCodeForPath(menu.path());
            if (viewCode != null && permissionCoversView(permissionCodes, viewCode)) {
                addMenuWithAncestors(menuIds, menusById, menu.id());
                addPermissionCode(permissionIds, permissionCodes, permissionIdsByCode, viewCode);
            }
        }

        return new AccessSelection(new ArrayList<>(menuIds), new ArrayList<>(permissionIds));
    }

    private List<Map<String, Object>> rowsOrEmpty(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private void addPermissionCode(
            Set<Long> permissionIds,
            Set<String> permissionCodes,
            Map<String, Long> permissionIdsByCode,
            String code) {
        if (code == null) {
            return;
        }
        Long id = permissionIdsByCode.get(code);
        if (id != null) {
            permissionIds.add(id);
            permissionCodes.add(code);
        }
    }

    private void addMenuWithAncestors(Set<Long> menuIds, Map<Long, MenuAccessRow> menusById, Long menuId) {
        Long currentId = menuId;
        while (currentId != null) {
            MenuAccessRow menu = menusById.get(currentId);
            if (menu == null || !menuIds.add(menu.id())) {
                if (menu != null) {
                    currentId = menu.parentId();
                    continue;
                }
                break;
            }
            currentId = menu.parentId();
        }
    }

    private String viewPermissionCodeForPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return PATH_VIEW_PERMISSION_CODE.get(path.trim());
    }

    private String viewPermissionCodeFor(String code) {
        if (code == null || code.isBlank() || code.endsWith(":*")) {
            return null;
        }
        String normalized = code.trim();
        if (normalized.endsWith(":view")) {
            return normalized;
        }
        if (normalized.startsWith("finance:invoice:")) {
            return "finance:bill:view";
        }
        if (normalized.startsWith("energy:reading:")) {
            return "energy:view";
        }
        if (normalized.startsWith("tenant:portal:")) {
            return "tenant:view";
        }
        int lastSeparator = normalized.lastIndexOf(':');
        if (lastSeparator <= 0) {
            return null;
        }
        return normalized.substring(0, lastSeparator) + ":view";
    }

    private boolean permissionCoversView(Set<String> permissionCodes, String viewCode) {
        if (permissionCodes.contains(viewCode)) {
            return true;
        }
        for (String code : permissionCodes) {
            if (code.endsWith(":*")) {
                String prefix = code.substring(0, code.length() - 1);
                if (viewCode.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private List<Long> ids(Map<String, Object> body) {
        return ids(body, "ids");
    }

    private List<Long> ids(Map<String, Object> body, String... keys) {
        Object raw = null;
        if (body != null) {
            for (String key : keys) {
                if (body.containsKey(key)) {
                    raw = body.get(key);
                    break;
                }
            }
        }
        List<Long> ids = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Number number) {
                    ids.add(number.longValue());
                } else if (item != null && !String.valueOf(item).isBlank()) {
                    try {
                        ids.add(Long.valueOf(String.valueOf(item)));
                    } catch (NumberFormatException exception) {
                        throw new BusinessException(400, "Selection contains an invalid identifier");
                    }
                }
            }
        }
        return ids;
    }

    private List<Long> requiredIds(Map<String, Object> body, String message, String... keys) {
        requireAnySelectionKey(body, message, keys);
        validateIterableSelection(body, keys);
        return ids(body, keys);
    }

    private List<String> strings(Map<String, Object> body, String... keys) {
        Object raw = firstValue(body, keys);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof Iterable<?> iterable)) {
            throw new BusinessException(400, "Selection must be an array");
        }
        Set<String> values = new LinkedHashSet<>();
        for (Object item : iterable) {
            String value = textValue(item);
            if (value != null) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    private void requireAnySelectionKey(Map<String, Object> body, String message, String... keys) {
        if (body == null) {
            throw new BusinessException(400, message);
        }
        boolean found = false;
        for (String key : keys) {
            if (body.containsKey(key)) {
                found = true;
            }
        }
        if (!found) {
            throw new BusinessException(400, message);
        }
        validateIterableSelection(body, keys);
    }

    private void validateIterableSelection(Map<String, Object> body, String... keys) {
        if (body == null) {
            return;
        }
        for (String key : keys) {
            if (body.containsKey(key) && !(body.get(key) instanceof Iterable<?>)) {
                throw new BusinessException(400, "Selection must be an array");
            }
        }
    }

    private Object firstValue(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String key : keys) {
            if (body.containsKey(key)) {
                return body.get(key);
            }
        }
        return null;
    }

    private void validateKnownIds(List<Long> requestedIds, List<Long> knownIds, String message) {
        Set<Long> known = new LinkedHashSet<>(knownIds == null ? List.of() : knownIds);
        if (!known.containsAll(requestedIds)) {
            throw new BusinessException(400, message);
        }
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds, Long operatorId) {
        List<Long> selected = new ArrayList<>(new LinkedHashSet<>(roleIds));
        validateKnownIds(selected, enabledRoleIds(), "角色选择已失效，请刷新后重试");
        List<Long> current = activeRoleIds(userId);
        for (Long roleId : current) {
            if (selected.contains(roleId)) continue;
            jdbcTemplate.update(
                    """
                    UPDATE sys_user_role
                    SET status = 'DISABLED', deleted = 1, updated_by = ?
                    WHERE user_id = ? AND role_id = ? AND deleted = 0
                    """,
                    operatorId,
                    userId,
                    roleId
            );
        }
        for (Long roleId : selected) {
            if (current.contains(roleId)) continue;
            int restored = jdbcTemplate.update(
                    """
                    UPDATE sys_user_role
                    SET status = 'ENABLED', deleted = 0, updated_by = ?
                    WHERE user_id = ? AND role_id = ? AND deleted = 1
                    """,
                    operatorId,
                    userId,
                    roleId
            );
            if (restored == 0) {
                jdbcTemplate.update(
                        "INSERT INTO sys_user_role (user_id, role_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                        userId,
                        roleId,
                        operatorId,
                        operatorId
                );
            }
        }
    }

    private List<Long> enabledRoleIds() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM sys_role WHERE deleted = 0 AND status = 'ENABLED'",
                Long.class
        );
    }

    private List<Long> activeRoleIds(Long userId) {
        return jdbcTemplate.queryForList(
                """
                SELECT role_id
                FROM sys_user_role
                WHERE user_id = ? AND deleted = 0 AND status = 'ENABLED'
                ORDER BY role_id
                """,
                Long.class,
                userId
        );
    }

    private List<Long> requestedUserRoleIds(Map<String, Object> body) {
        Long primary = JdbcMaps.longVal(body, "primaryRoleId");
        if (primary != null) return List.of(primary);
        List<Long> roleIds = ids(body, "roleIds", "ids");
        return roleIds.isEmpty() ? List.of() : List.of(roleIds.getFirst());
    }

    private Long primaryRoleId(Map<String, Object> body, List<Long> roleIds) {
        Long requested = JdbcMaps.longVal(body, "primaryRoleId");
        if (requested != null) return requested;
        return roleIds.isEmpty() ? null : roleIds.getFirst();
    }

    private void validateUserSettings(
            Long userId,
            String phone,
            String status,
            boolean webLoginEnabled,
            boolean mobileEnabled,
            List<Long> roleIds) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(400, "账号状态无效");
        }
        if (roleIds.isEmpty()) {
            throw new BusinessException(400, "请选择主要角色");
        }
        validateKnownIds(roleIds, enabledRoleIds(), "角色选择已失效，请刷新后重试");
        if (mobileEnabled) {
            if (!"ENABLED".equals(status)) {
                throw new BusinessException(400, "停用用户不能开启移动工作台");
            }
            if (phone == null || !phone.matches("^1\\d{10}$")) {
                throw new BusinessException(400, "开启移动工作台时必须填写有效手机号");
            }
            boolean roleAllowsMobileWorkbench = roleIds.stream().anyMatch(this::roleAllowsMobileWorkbench);
            if (!roleAllowsMobileWorkbench) {
                throw new BusinessException(400, "所选角色未勾选物业移动工作站权限");
            }
        }
        if (phone != null) {
            Integer duplicate = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM sys_user
                    WHERE phone = ? AND deleted = 0 AND (? IS NULL OR id <> ?)
                    """,
                    Integer.class,
                    phone,
                    userId,
                    userId
            );
            if (duplicate != null && duplicate > 0) {
                throw new BusinessException(400, "手机号已被其他内部员工使用");
            }
        }
    }

    private boolean roleAllowsMobileWorkbench(Long roleId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM sys_role role_record
                INNER JOIN sys_role_permission role_permission
                  ON role_permission.role_id = role_record.id
                 AND role_permission.deleted = 0
                 AND role_permission.status IN ('ACTIVE', 'ENABLED')
                INNER JOIN sys_permission permission_record
                  ON permission_record.id = role_permission.permission_id
                 AND permission_record.deleted = 0
                 AND permission_record.status IN ('ACTIVE', 'ENABLED')
                WHERE role_record.id = ?
                  AND role_record.deleted = 0
                  AND role_record.status IN ('ACTIVE', 'ENABLED')
                  AND permission_record.code IN ('staff-mobile:access', 'staff-mobile:*', '*')
                """,
                Integer.class,
                roleId
        );
        return count != null && count > 0;
    }

    private String normalizedPhone(String phone) {
        if (phone == null) return null;
        String normalized = phone.replaceAll("[^0-9]", "");
        if (normalized.startsWith("86") && normalized.length() == 13) {
            normalized = normalized.substring(2);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String generatedMobileUsername() {
        return "mobile_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private void ensureUsernameAvailable(String username, Long excludedUserId) {
        Integer duplicate = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM sys_user
                WHERE username = ? AND deleted = 0 AND id <> ?
                """,
                Integer.class,
                username,
                excludedUserId
        );
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
    }

    private void replaceRoleMenus(Long roleId, List<Long> menuIds) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        for (Long menuId : menuIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                    roleId, menuId, 0L, 0L);
        }
    }

    private void replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        for (Long permissionId : permissionIds) {
            jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                    roleId, permissionId, 0L, 0L);
        }
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

    private void attachUserRoles(List<Map<String, Object>> users) {
        if (users.isEmpty()) {
            return;
        }

        Map<Long, Map<String, Object>> usersById = new LinkedHashMap<>();
        for (Map<String, Object> user : users) {
            Long userId = longValue(user.get("id"));
            if (userId != null) {
                user.put("roleIds", new ArrayList<Long>());
                user.put("roles", new ArrayList<String>());
                user.put("roleNames", new ArrayList<String>());
                usersById.put(userId, user);
            }
        }
        if (usersById.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(usersById.size(), "?"));
        List<Map<String, Object>> roleRows = jdbcTemplate.queryForList(
                """
                SELECT ur.user_id AS userId, r.id AS roleId, r.code AS roleCode, r.name AS roleName
                FROM sys_user_role ur
                INNER JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.deleted = 0
                  AND ur.status = 'ENABLED'
                  AND r.deleted = 0
                  AND r.status = 'ENABLED'
                  AND ur.user_id IN (%s)
                ORDER BY ur.user_id ASC, r.id ASC
                """.formatted(placeholders),
                usersById.keySet().toArray()
        );

        for (Map<String, Object> roleRow : roleRows) {
            Map<String, Object> user = usersById.get(longValue(roleRow.get("userId")));
            if (user == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Long> roleIds = (List<Long>) user.get("roleIds");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) user.get("roles");
            @SuppressWarnings("unchecked")
            List<String> roleNames = (List<String>) user.get("roleNames");
            Long roleId = longValue(roleRow.get("roleId"));
            if (roleId != null) {
                roleIds.add(roleId);
            }
            roles.add(String.valueOf(roleRow.get("roleCode")));
            roleNames.add(String.valueOf(roleRow.get("roleName")));
        }
        for (Map<String, Object> user : usersById.values()) {
            Long primaryRoleId = longValue(user.get("primaryRoleId"));
            @SuppressWarnings("unchecked")
            List<Long> roleIds = (List<Long>) user.get("roleIds");
            @SuppressWarnings("unchecked")
            List<String> roleNames = (List<String>) user.get("roleNames");
            int primaryIndex = primaryRoleId == null ? -1 : roleIds.indexOf(primaryRoleId);
            if (primaryIndex < 0 && !roleIds.isEmpty()) {
                primaryIndex = 0;
                user.put("primaryRoleId", roleIds.getFirst());
            }
            user.put("primaryRoleName", primaryIndex >= 0 ? roleNames.get(primaryIndex) : null);
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }

    private RoleSnapshot roleForUpdate(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, code, role_type AS roleType, status
                FROM sys_role
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Role not found");
        }
        Map<String, Object> row = rows.getFirst();
        return new RoleSnapshot(
                longValue(row.get("id")),
                normalizedRoleValue(row.get("code")),
                normalizedRoleValue(row.get("roleType")),
                normalizedRoleValue(row.get("status"))
        );
    }

    private void ensureRoleStatusCanChange(RoleSnapshot role, String requestedStatus) {
        String nextStatus = normalizedRoleValue(requestedStatus);
        if ("ENABLED".equals(nextStatus)) {
            return;
        }
        if (isCoreAdminRole(role.code())) {
            throw new BusinessException(400, "ADMIN和SUPER_ADMIN系统管理员角色不能禁用");
        }
        if (!"SYSTEM".equals(role.roleType()) || !"ENABLED".equals(role.status())) {
            return;
        }
        List<Long> enabledSystemRoleIds = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM sys_role
                WHERE role_type = 'SYSTEM' AND status = 'ENABLED' AND deleted = 0
                ORDER BY id
                FOR UPDATE
                """,
                Long.class
        );
        if (enabledSystemRoleIds.size() <= 1 && enabledSystemRoleIds.contains(role.id())) {
            throw new BusinessException(400, "必须至少保留一个启用的系统管理员角色");
        }
    }

    private boolean isCoreAdminRole(String roleCode) {
        return roleCode != null && CORE_ADMIN_ROLE_CODES.contains(roleCode);
    }

    private UserSnapshot userForUpdate(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, username, status, mobile_enabled AS mobileEnabled,
                       web_login_enabled AS webLoginEnabled
                FROM sys_user
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "User not found");
        }
        Map<String, Object> row = rows.getFirst();
        return new UserSnapshot(
                longValue(row.get("id")),
                textValue(row.get("username")),
                normalizedRoleValue(row.get("status")),
                truthy(row.get("mobileEnabled")),
                truthy(row.get("webLoginEnabled"))
        );
    }

    private void protectUserMutation(
            AuthPrincipal principal,
            UserSnapshot target,
            String nextStatus,
            List<Long> nextRoleIds,
            boolean deleting) {
        if (target.id().equals(principal.userId()) && (deleting || !"ENABLED".equals(nextStatus))) {
            throw new BusinessException(400, "当前登录用户不能停用或删除自己");
        }
        List<String> targetRoleCodes = activeRoleCodes(target.id());
        boolean operatorSuper = principal.roles().contains("SUPER_ADMIN")
                || hasCurrentRole(principal.userId(), "SUPER_ADMIN");
        boolean targetSuper = targetRoleCodes.contains("SUPER_ADMIN");
        if (targetSuper && !operatorSuper && !target.id().equals(principal.userId())) {
            throw new BusinessException(403, "普通管理员不能修改超级管理员账号");
        }

        List<String> nextRoleCodes = roleCodes(nextRoleIds);
        if (target.id().equals(principal.userId())
                && targetRoleCodes.stream().anyMatch(CORE_ADMIN_ROLE_CODES::contains)
                && nextRoleCodes.stream().noneMatch(CORE_ADMIN_ROLE_CODES::contains)) {
            throw new BusinessException(400, "当前用户不能移除自己的最后一个管理角色");
        }
        if (targetSuper && (deleting || !"ENABLED".equals(nextStatus) || !nextRoleCodes.contains("SUPER_ADMIN"))) {
            List<Long> enabledSuperUsers = jdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT user_record.id
                    FROM sys_user user_record
                    INNER JOIN sys_user_role user_role
                      ON user_role.user_id = user_record.id
                     AND user_role.deleted = 0
                     AND user_role.status = 'ENABLED'
                    INNER JOIN sys_role role_record
                      ON role_record.id = user_role.role_id
                     AND role_record.code = 'SUPER_ADMIN'
                     AND role_record.deleted = 0
                     AND role_record.status = 'ENABLED'
                    WHERE user_record.deleted = 0
                      AND user_record.status = 'ENABLED'
                    ORDER BY user_record.id
                    FOR UPDATE
                    """,
                    Long.class
            );
            if (enabledSuperUsers.size() <= 1 && enabledSuperUsers.contains(target.id())) {
                throw new BusinessException(400, "不能停用或移除最后一个超级管理员");
            }
        }
    }

    private List<String> activeRoleCodes(Long userId) {
        return jdbcTemplate.queryForList(
                """
                SELECT role_record.code
                FROM sys_user_role user_role
                INNER JOIN sys_role role_record
                  ON role_record.id = user_role.role_id
                 AND role_record.deleted = 0
                 AND role_record.status = 'ENABLED'
                WHERE user_role.user_id = ?
                  AND user_role.deleted = 0
                  AND user_role.status = 'ENABLED'
                """,
                String.class,
                userId
        );
    }

    private List<String> roleCodes(List<Long> roleIds) {
        if (roleIds.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
        return jdbcTemplate.queryForList(
                "SELECT code FROM sys_role WHERE id IN (" + placeholders + ") AND deleted = 0 AND status = 'ENABLED'",
                String.class,
                roleIds.toArray()
        );
    }

    private boolean hasCurrentRole(Long userId, String roleCode) {
        return activeRoleCodes(userId).stream().anyMatch(roleCode::equals);
    }

    private boolean isSuperAdmin(AuthPrincipal principal) {
        return principal.roles().contains("SUPER_ADMIN")
                || hasCurrentRole(principal.userId(), "SUPER_ADMIN");
    }

    private void validateUserRoleGrantScope(
            AuthPrincipal principal,
            Long targetUserId,
            List<Long> requestedRoleIds) {
        if (isSuperAdmin(principal)) {
            return;
        }
        List<Long> existingTargetRoleIds = targetUserId == null ? List.of() : activeRoleIds(targetUserId);
        for (Long roleId : requestedRoleIds) {
            List<String> codes = jdbcTemplate.queryForList(
                    "SELECT code FROM sys_role WHERE id = ? AND deleted = 0 AND status = 'ENABLED'",
                    String.class,
                    roleId
            );
            if (!codes.isEmpty() && isCoreAdminRole(codes.getFirst())
                    && !existingTargetRoleIds.contains(roleId)) {
                throw new BusinessException(403, "只有超级管理员可以新增或授予系统管理员角色");
            }
        }

        Set<Long> operatorPermissionIds = new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                SELECT DISTINCT role_permission.permission_id
                FROM sys_user_role user_role
                INNER JOIN sys_role_permission role_permission
                  ON role_permission.role_id = user_role.role_id
                 AND role_permission.deleted = 0
                 AND role_permission.status = 'ENABLED'
                WHERE user_role.user_id = ?
                  AND user_role.deleted = 0
                  AND user_role.status = 'ENABLED'
                """,
                Long.class,
                principal.userId()
        ));
        Set<Long> requestedPermissionIds = new LinkedHashSet<>();
        for (Long roleId : requestedRoleIds) {
            requestedPermissionIds.addAll(jdbcTemplate.queryForList(
                    "SELECT permission_id FROM sys_role_permission WHERE role_id = ? AND deleted = 0 AND status = 'ENABLED'",
                    Long.class,
                    roleId
            ));
        }
        if (!operatorPermissionIds.containsAll(requestedPermissionIds)) {
            throw new BusinessException(403, "不能授予超出当前管理员自身范围的角色权限");
        }
    }

    private void requireSuperForCoreRoleDefinition(
            AuthPrincipal principal,
            String roleCode,
            String roleType) {
        if ((isCoreAdminRole(roleCode) || "SYSTEM".equals(roleType)) && !isSuperAdmin(principal)) {
            throw new BusinessException(403, "只有超级管理员可以创建系统管理员角色");
        }
    }

    private void requireSuperForCoreRoleMutation(AuthPrincipal principal, RoleSnapshot role) {
        if ((isCoreAdminRole(role.code()) || "SYSTEM".equals(role.roleType())) && !isSuperAdmin(principal)) {
            throw new BusinessException(403, "只有超级管理员可以修改系统管理员角色");
        }
    }

    private void validateRoleGrantScope(AuthPrincipal principal, Long targetRoleId, AccessSelection selection) {
        boolean operatorSuper = isSuperAdmin(principal);
        RoleSnapshot target = operatorSuper ? null : roleForUpdate(targetRoleId);
        if (!operatorSuper && isCoreAdminRole(target.code())) {
            throw new BusinessException(403, "只有超级管理员可以修改系统管理员角色");
        }
        if (operatorSuper) return;

        List<Long> existingPermissionIds = jdbcTemplate.queryForList(
                "SELECT permission_id FROM sys_role_permission WHERE role_id = ? AND deleted = 0 AND status = 'ENABLED'",
                Long.class,
                targetRoleId
        );
        Set<Long> addedPermissionIds = new LinkedHashSet<>(selection.permissionIds());
        addedPermissionIds.removeAll(existingPermissionIds);
        if (!addedPermissionIds.isEmpty()) {
            Set<Long> operatorPermissionIds = new LinkedHashSet<>(jdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT role_permission.permission_id
                    FROM sys_user_role user_role
                    INNER JOIN sys_role role_record
                      ON role_record.id = user_role.role_id
                     AND role_record.deleted = 0
                     AND role_record.status = 'ENABLED'
                    INNER JOIN sys_role_permission role_permission
                      ON role_permission.role_id = role_record.id
                     AND role_permission.deleted = 0
                     AND role_permission.status = 'ENABLED'
                    WHERE user_role.user_id = ?
                      AND user_role.deleted = 0
                      AND user_role.status = 'ENABLED'
                    """,
                    Long.class,
                    principal.userId()
            ));
            if (!operatorPermissionIds.containsAll(addedPermissionIds)) {
                throw new BusinessException(403, "不能授予超出当前管理员自身范围的权限");
            }
        }

        List<Long> existingMenuIds = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? AND deleted = 0 AND status = 'ENABLED'",
                Long.class,
                targetRoleId
        );
        Set<Long> addedMenuIds = new LinkedHashSet<>(selection.menuIds());
        addedMenuIds.removeAll(existingMenuIds);
        if (!addedMenuIds.isEmpty()) {
            Set<Long> operatorMenuIds = new LinkedHashSet<>(jdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT role_menu.menu_id
                    FROM sys_user_role user_role
                    INNER JOIN sys_role role_record
                      ON role_record.id = user_role.role_id
                     AND role_record.deleted = 0
                     AND role_record.status = 'ENABLED'
                    INNER JOIN sys_role_menu role_menu
                      ON role_menu.role_id = role_record.id
                     AND role_menu.deleted = 0
                     AND role_menu.status = 'ENABLED'
                    WHERE user_role.user_id = ?
                      AND user_role.deleted = 0
                      AND user_role.status = 'ENABLED'
                    """,
                    Long.class,
                    principal.userId()
            ));
            if (!operatorMenuIds.containsAll(addedMenuIds)) {
                throw new BusinessException(403, "不能授予超出当前管理员自身范围的菜单");
            }
        }
    }

    private void invalidateRoleStaffBindings(Long roleId, Long operatorId, String reason) {
        List<Long> affectedUserIds = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT user_id
                FROM sys_user_role
                WHERE role_id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                """,
                Long.class,
                roleId
        );
        mobileIdentityService.invalidateIneligibleStaffBindings(
                affectedUserIds,
                operatorId,
                reason
        );
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.intValue() != 0;
        return "1".equals(String.valueOf(value)) || "TRUE".equalsIgnoreCase(String.valueOf(value));
    }

    private String normalizedRoleValue(Object value) {
        String text = textValue(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private String statusValue(String requestStatus, Map<String, Object> body) {
        if (requestStatus != null && !requestStatus.isBlank()) {
            return requestStatus.trim();
        }
        return JdbcMaps.strOr(body, "ENABLED", "status");
    }

    // Test and direct-call compatibility. MVC uses the annotated overloads above.
    public ApiResponse<Void> createUser(Map<String, Object> body) {
        return createUser(systemTestPrincipal(), body);
    }

    public ApiResponse<Void> updateUser(Long id, Map<String, Object> body) {
        return updateUser(systemTestPrincipal(), id, body);
    }

    public ApiResponse<Void> saveUserRoles(Long id, Map<String, Object> body) {
        return saveUserRoles(systemTestPrincipal(), id, body);
    }

    public ApiResponse<Void> updateUserStatus(Long id, String status, Map<String, Object> body) {
        return updateUserStatus(systemTestPrincipal(), id, status, body);
    }

    public ApiResponse<Void> deleteUser(Long id) {
        return deleteUser(systemTestPrincipal(), id);
    }

    public ApiResponse<Void> saveRoleAccess(Long id, Map<String, Object> body) {
        return saveRoleAccess(systemTestPrincipal(), id, body);
    }

    public ApiResponse<Void> createRole(Map<String, Object> body) {
        return createRole(systemTestPrincipal(), body);
    }

    public ApiResponse<Void> updateRole(Long id, Map<String, Object> body) {
        return updateRole(systemTestPrincipal(), id, body);
    }

    public ApiResponse<Void> updateRoleStatus(Long id, String status, Map<String, Object> body) {
        return updateRoleStatus(systemTestPrincipal(), id, status, body);
    }

    public ApiResponse<Void> deleteRole(Long id) {
        return deleteRole(systemTestPrincipal(), id);
    }

    public ApiResponse<Void> saveRoleMenus(Long id, Map<String, Object> body) {
        return saveRoleMenus(systemTestPrincipal(), id, body);
    }

    public ApiResponse<Void> saveRolePermissions(Long id, Map<String, Object> body) {
        return saveRolePermissions(systemTestPrincipal(), id, body);
    }

    private AuthPrincipal systemTestPrincipal() {
        return new AuthPrincipal(0L, "system-test", "SYSTEM", null, List.of("SUPER_ADMIN"), List.of("*"));
    }

    private record AccessSelection(List<Long> menuIds, List<Long> permissionIds) {
    }

    private record MenuAccessRow(Long id, String code, Long parentId, String path) {
    }

    private record RoleSnapshot(Long id, String code, String roleType, String status) {
    }

    private record UserSnapshot(
            Long id,
            String username,
            String status,
            boolean mobileEnabled,
            boolean webLoginEnabled) {
    }
}

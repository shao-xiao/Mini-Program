package com.dehui.property.modules.system.service;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.system.dto.LoginRequest;
import com.dehui.property.modules.system.dto.LoginResponse;
import com.dehui.property.modules.system.dto.UserAccessResponse;
import com.dehui.property.security.AuthPrincipal;
import com.dehui.property.security.TokenService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemAuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public LoginResponse login(LoginRequest request) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                """
                SELECT id, username, password_hash, real_name, status, web_login_enabled
                FROM sys_user
                WHERE username = ? AND deleted = 0
                LIMIT 1
                """,
                request.username()
        );

        if (users.isEmpty()) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        Map<String, Object> user = users.getFirst();
        String status = String.valueOf(user.get("status"));
        if (!"ENABLED".equals(status)) {
            recordLogin(user, "DISABLED");
            throw BusinessException.forbidden("账号已停用");
        }
        if (!truthy(user.get("web_login_enabled"))) {
            recordLogin(user, "WEB_LOGIN_DISABLED");
            throw BusinessException.forbidden("该员工未开启管理后台登录");
        }

        String passwordHash = String.valueOf(user.get("password_hash"));
        if (!passwordEncoder.matches(request.password(), passwordHash)) {
            recordLogin(user, "PASSWORD_ERROR");
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        Long userId = ((Number) user.get("id")).longValue();
        jdbcTemplate.update(
                "UPDATE sys_user SET last_login_at = NOW(), updated_by = ? WHERE id = ? AND deleted = 0",
                userId,
                userId
        );
        recordLogin(user, "SUCCESS");
        List<String> roles = queryRoles(userId);
        List<String> permissions = queryPermissions(userId, roles);
        List<Map<String, Object>> menus = queryMenus(userId, roles);
        Map<String, Object> userInfo = userInfo(userId, user, roles);
        AuthPrincipal principal = new AuthPrincipal(
                userId,
                String.valueOf(user.get("username")),
                "SYSTEM",
                null,
                roles,
                permissions
        );
        String token = tokenService.createToken(principal);
        return new LoginResponse(
                token,
                userId,
                principal.username(),
                String.valueOf(user.get("real_name")),
                roles,
                permissions,
                menus,
                userInfo
        );
    }

    public LoginResponse currentUser(AuthPrincipal principal) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                """
                SELECT id, username, real_name, status
                FROM sys_user
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """,
                principal.userId()
        );
        if (users.isEmpty()) {
            throw BusinessException.unauthorized("鐢ㄦ埛涓嶅瓨鍦ㄦ垨宸插け鏁?");
        }

        Map<String, Object> user = users.getFirst();
        List<String> roles = queryRoles(principal.userId());
        List<String> permissions = queryPermissions(principal.userId(), roles);
        List<Map<String, Object>> menus = queryMenus(principal.userId(), roles);
        Map<String, Object> userInfo = userInfo(principal.userId(), user, roles);
        return new LoginResponse(
                null,
                principal.userId(),
                String.valueOf(user.get("username")),
                String.valueOf(user.get("real_name")),
                roles,
                permissions,
                menus,
                userInfo
        );
    }

    public UserAccessResponse currentAccess(AuthPrincipal principal) {
        List<String> roles = queryRoles(principal.userId());
        List<String> permissions = queryPermissions(principal.userId(), roles);
        List<Map<String, Object>> menus = queryMenus(principal.userId(), roles);
        return new UserAccessResponse(roles, permissions, menus);
    }

    private List<String> queryRoles(Long userId) {
        return jdbcTemplate.queryForList(
                """
                SELECT r.code
                FROM sys_role r
                INNER JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ?
                  AND ur.deleted = 0
                  AND ur.status = 'ENABLED'
                  AND r.deleted = 0
                  AND r.status = 'ENABLED'
                """,
                String.class,
                userId
        );
    }

    private List<String> queryPermissions(Long userId, List<String> roles) {
        if (roles.contains("SUPER_ADMIN")) {
            return jdbcTemplate.queryForList(
                    """
                    SELECT code
                    FROM sys_permission
                    WHERE deleted = 0 AND status = 'ENABLED'
                    ORDER BY id
                    """,
                    String.class
            );
        }
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT p.code
                FROM sys_permission p
                INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
                INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
                INNER JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                  AND p.deleted = 0
                  AND p.status = 'ENABLED'
                  AND rp.deleted = 0
                  AND rp.status = 'ENABLED'
                  AND ur.deleted = 0
                  AND ur.status = 'ENABLED'
                  AND r.deleted = 0
                  AND r.status = 'ENABLED'
                """,
                String.class,
                userId
        );
    }

    private List<Map<String, Object>> queryMenus(Long userId, List<String> roles) {
        String sql;
        Object[] args;
        if (roles.contains("SUPER_ADMIN")) {
            sql = """
                  SELECT id, code, code AS menuCode, parent_id AS parentId, name, name AS menuName,
                         path, component, icon, sort_order AS sortOrder, status
                  FROM sys_menu
                  WHERE deleted = 0 AND status = 'ENABLED'
                  ORDER BY sort_order ASC, id ASC
                  """;
            args = new Object[0];
        } else {
            sql = """
                  SELECT DISTINCT m.id, m.code, m.code AS menuCode, m.parent_id AS parentId,
                         m.name, m.name AS menuName, m.path, m.component, m.icon,
                         m.sort_order AS sortOrder, m.status
                  FROM sys_menu m
                  INNER JOIN sys_role_menu rm ON rm.menu_id = m.id AND rm.deleted = 0 AND rm.status = 'ENABLED'
                  INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id AND ur.deleted = 0 AND ur.status = 'ENABLED'
                  INNER JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 'ENABLED'
                  WHERE ur.user_id = ?
                    AND m.deleted = 0
                    AND m.status = 'ENABLED'
                  ORDER BY m.sort_order ASC, m.id ASC
                  """;
            args = new Object[] { userId };
        }
        return menuTree(jdbcTemplate.queryForList(sql, args));
    }

    private void recordLogin(Map<String, Object> user, String result) {
        Long userId = user.get("id") instanceof Number number ? number.longValue() : null;
        jdbcTemplate.update(
                """
                INSERT INTO log_login
                  (code, user_type, user_id, username, login_result, created_by, updated_by)
                VALUES (?, 'SYSTEM', ?, ?, ?, ?, ?)
                """,
                "LOGIN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                userId,
                String.valueOf(user.get("username")),
                result,
                userId,
                userId
        );
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.intValue() != 0;
        return "1".equals(String.valueOf(value)) || "TRUE".equalsIgnoreCase(String.valueOf(value));
    }

    private List<Map<String, Object>> menuTree(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> node = new LinkedHashMap<>(row);
            node.put("title", node.get("menuName"));
            node.put("index", node.get("menuCode") != null ? node.get("menuCode") : node.get("path"));
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

    private Map<String, Object> userInfo(Long userId, Map<String, Object> user, List<String> roles) {
        return Map.of(
                "id", userId,
                "userId", userId,
                "username", String.valueOf(user.get("username")),
                "realName", String.valueOf(user.get("real_name")),
                "roles", roles
        );
    }
}

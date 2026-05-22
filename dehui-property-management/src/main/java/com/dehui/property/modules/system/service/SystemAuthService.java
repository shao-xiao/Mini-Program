package com.dehui.property.modules.system.service;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.system.dto.LoginRequest;
import com.dehui.property.modules.system.dto.LoginResponse;
import com.dehui.property.security.AuthPrincipal;
import com.dehui.property.security.TokenService;
import java.util.List;
import java.util.Map;
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
                SELECT id, username, password_hash, real_name, status
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
            throw BusinessException.forbidden("账号已停用");
        }

        String passwordHash = String.valueOf(user.get("password_hash"));
        if (!passwordEncoder.matches(request.password(), passwordHash)) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        Long userId = ((Number) user.get("id")).longValue();
        List<String> roles = queryRoles(userId);
        List<String> permissions = queryPermissions(userId);
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
                permissions
        );
    }

    private List<String> queryRoles(Long userId) {
        return jdbcTemplate.queryForList(
                """
                SELECT r.code
                FROM sys_role r
                INNER JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND r.deleted = 0 AND r.status = 'ENABLED'
                """,
                String.class,
                userId
        );
    }

    private List<String> queryPermissions(Long userId) {
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
                  AND r.deleted = 0
                  AND r.status = 'ENABLED'
                """,
                String.class,
                userId
        );
    }
}

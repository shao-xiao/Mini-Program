package com.dehui.property.config;

import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.service.SystemUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final SystemUserService systemUserService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();
        System.out.println(">>> AuthInterceptor URI = " + uri);

        // ===== 放行认证接口 + 基础数据 =====
        if (uri.contains("/login")
                || uri.startsWith("/api/system/login")
                || uri.startsWith("/api/buildings")
                || uri.startsWith("/api/error")) {
            return true;
        }

        String token = request.getHeader("Authorization");

        if (token == null || token.isBlank()) {
            writeUnauthorized(response, "未登录");
            return false;
        }

        // 兼容 Authorization: Bearer xxx
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        SysUser user = systemUserService.getByToken(token);

        if (user == null) {
            writeUnauthorized(response, "token无效");
            return false;
        }

        // 基础资产：ADMIN / MANAGER / STAFF
        if (uri.startsWith("/api/buildings")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "STAFF"))) {
                writeForbidden(response, "无权限访问基础资产接口");
                return false;
            }
        }

        // 系统管理：仅 ADMIN
        if (uri.startsWith("/api/system")) {
            if (!systemUserService.hasRole(token, "ADMIN")) {
                writeForbidden(response, "无权限访问系统管理接口");
                return false;
            }
        }

        // 租户：ADMIN / MANAGER / STAFF / FINANCE
        if (uri.startsWith("/api/tenants") || uri.startsWith("/api/tenant")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "STAFF", "FINANCE"))) {
                writeForbidden(response, "无权限访问租户接口");
                return false;
            }
        }

        // 合同：ADMIN / MANAGER / FINANCE
        if (uri.startsWith("/api/contracts")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "FINANCE"))) {
                writeForbidden(response, "无权限访问合同接口");
                return false;
            }
        }

        // 账单：ADMIN / MANAGER / FINANCE
        if (uri.startsWith("/api/bills")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "FINANCE"))) {
                writeForbidden(response, "无权限访问账单接口");
                return false;
            }
        }

        // 收费规则：ADMIN / MANAGER / FINANCE
        if (uri.startsWith("/api/feerules")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "FINANCE"))) {
                writeForbidden(response, "无权限访问收费规则接口");
                return false;
            }
        }

        // 停车：ADMIN / MANAGER / FINANCE
        if (uri.startsWith("/api/parking")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "FINANCE"))) {
                writeForbidden(response, "无权限访问停车接口");
                return false;
            }
        }

        // 访客：ADMIN / MANAGER / SECURITY
        if (uri.startsWith("/api/visitors")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "SECURITY"))) {
                writeForbidden(response, "无权限访问访客接口");
                return false;
            }
        }

        // 工单：ADMIN / MANAGER / STAFF / SECURITY / CLEANER
        if (uri.startsWith("/api/workorders")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "STAFF", "SECURITY", "CLEANER"))) {
                writeForbidden(response, "无权限访问工单接口");
                return false;
            }
        }

        // 公告：ADMIN / MANAGER
        if (uri.startsWith("/api/announcements")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER"))) {
                writeForbidden(response, "无权限访问公告接口");
                return false;
            }
        }

        // AI：ADMIN / MANAGER
        if (uri.startsWith("/api/ai")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER"))) {
                writeForbidden(response, "无权限访问AI分析接口");
                return false;
            }
        }

        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(403);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
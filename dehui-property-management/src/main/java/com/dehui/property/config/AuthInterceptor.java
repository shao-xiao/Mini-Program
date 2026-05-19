package com.dehui.property.config;

import com.dehui.property.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dehui.property.modules.mobile.service.MobileAuthService;
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
    private final MobileAuthService mobileAuthService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        System.out.println(">>> AuthInterceptor URI = " + uri + ", METHOD = " + method);

        if (uri.contains("/login")
                || uri.startsWith("/api/ping")
                || uri.startsWith("/api/system/login")
                || uri.startsWith("/api/mobile/auth/dev-login")
                || uri.startsWith("/api/mobile/dev/fixtures")
                || ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/uploads/"))
                || ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/mobile/announcements"))
                || uri.startsWith("/api/mobile/investment")
                || uri.startsWith("/api/error")) {
            return true;
        }

        String token = request.getHeader("Authorization");

        if (token == null || token.isBlank()) {
            writeUnauthorized(response, "未登录");
            return false;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (uri.startsWith("/api/mobile")) {
            if (mobileAuthService.getByToken(token) == null) {
                writeUnauthorized(response, "移动端登录已过期");
                return false;
            }
            return true;
        }

        SysUser user = systemUserService.getByToken(token);

        if (user == null) {
            writeUnauthorized(response, "token无效");
            return false;
        }

        // ===== 个人账号：所有已登录用户均可修改自己的密码 =====
        if (uri.startsWith("/api/system/me/password")) {
            return true;
        }

        // ===== 系统管理：仅 ADMIN =====
        if (uri.startsWith("/api/system")) {
            if (!systemUserService.hasRole(token, "ADMIN")) {
                writeForbidden(response, "无权限访问系统管理接口");
                return false;
            }
        }

        // ===== 基础资产：ADMIN / MANAGER / STAFF =====
        if (uri.startsWith("/api/buildings")
                || uri.startsWith("/api/floors")
                || uri.startsWith("/api/rooms")
                || uri.startsWith("/api/assets")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "STAFF"))) {
                writeForbidden(response, "无权限访问基础资产接口");
                return false;
            }
        }

        // ===== 租户：ADMIN / MANAGER / STAFF / FINANCE =====
        if (uri.startsWith("/api/tenants") || uri.startsWith("/api/tenant")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "STAFF", "FINANCE"))) {
                writeForbidden(response, "无权限访问租户接口");
                return false;
            }
        }

        // ===== 合同：ADMIN / MANAGER / FINANCE =====
        if (uri.startsWith("/api/contracts")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "FINANCE"))) {
                writeForbidden(response, "无权限访问合同接口");
                return false;
            }
        }

        // ===== 账单：动作级权限 =====
        if (uri.startsWith("/api/bills")) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!systemUserService.hasPermission(token, "bill:view")) {
                    writeForbidden(response, "无账单查看权限");
                    return false;
                }
            } else if ("POST".equalsIgnoreCase(method) && (uri.contains("/approve") || uri.contains("/reject"))) {
                if (!systemUserService.hasPermission(token, "bill:audit")) {
                    writeForbidden(response, "无账单审核权限");
                    return false;
                }
            } else if ("POST".equalsIgnoreCase(method) && uri.contains("/pay")) {
                if (!systemUserService.hasPermission(token, "bill:pay")) {
                    writeForbidden(response, "无账单收款权限");
                    return false;
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (!systemUserService.hasPermission(token, "bill:add")) {
                    writeForbidden(response, "无新增账单权限");
                    return false;
                }
            } else {
                writeForbidden(response, "无账单接口权限");
                return false;
            }
        }

        // ===== 收费规则：动作级权限 =====
        if (uri.startsWith("/api/feerules")) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!systemUserService.hasPermission(token, "feerule:view")) {
                    writeForbidden(response, "无收费规则查看权限");
                    return false;
                }
            } else if ("POST".equalsIgnoreCase(method) && uri.contains("/generate-bill")) {
                if (!systemUserService.hasPermission(token, "feerule:generate")) {
                    writeForbidden(response, "无生成账单权限");
                    return false;
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (!systemUserService.hasPermission(token, "feerule:add")) {
                    writeForbidden(response, "无新增收费规则权限");
                    return false;
                }
            } else {
                writeForbidden(response, "无收费规则接口权限");
                return false;
            }
        }

        // ===== 停车账单：动作级权限 =====
        if (uri.startsWith("/api/parking/bills")) {
            if ("GET".equalsIgnoreCase(method)) {
                if (!systemUserService.hasPermission(token, "parking-bill:view")) {
                    writeForbidden(response, "无停车账单查看权限");
                    return false;
                }
            } else if ("POST".equalsIgnoreCase(method) && uri.contains("/pay")) {
                if (!systemUserService.hasPermission(token, "parking-bill:pay")) {
                    writeForbidden(response, "无停车账单收款权限");
                    return false;
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (!systemUserService.hasPermission(token, "parking-bill:add")) {
                    writeForbidden(response, "无新增停车账单权限");
                    return false;
                }
            } else {
                writeForbidden(response, "无停车账单接口权限");
                return false;
            }
        }

        // ===== 车位管理：ADMIN / MANAGER / FINANCE =====
        if (uri.startsWith("/api/parking/spaces")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "FINANCE"))) {
                writeForbidden(response, "无权限访问车位接口");
                return false;
            }
        }

        // ===== 访客：ADMIN / MANAGER / SECURITY =====
        if (uri.startsWith("/api/visitors")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "SECURITY"))) {
                writeForbidden(response, "无权限访问访客接口");
                return false;
            }
        }

        // ===== 工单：ADMIN / MANAGER / STAFF / SECURITY / CLEANER =====
        if (uri.startsWith("/api/workorders")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "STAFF", "SECURITY", "CLEANER"))) {
                writeForbidden(response, "无权限访问工单接口");
                return false;
            }
        }

        // ===== 招商线索：ADMIN / MANAGER / STAFF =====
        if (uri.startsWith("/api/investment")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "STAFF"))) {
                writeForbidden(response, "无权限访问招商接口");
                return false;
            }
        }

        // ===== 会议室经营：ADMIN / MANAGER / STAFF / FINANCE =====
        if (uri.startsWith("/api/meetings")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER", "STAFF", "FINANCE"))) {
                writeForbidden(response, "无权限访问会议管理接口");
                return false;
            }
        }

        // ===== 公告：ADMIN / MANAGER =====
        if (uri.startsWith("/api/announcements")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER"))) {
                writeForbidden(response, "无权限访问公告接口");
                return false;
            }
        }

        // ===== AI：ADMIN / MANAGER =====
        if (uri.startsWith("/api/ai")) {
            if (!systemUserService.hasAnyRole(token, List.of("ADMIN", "MANAGER"))) {
                writeForbidden(response, "无权限访问AI分析接口");
                return false;
            }
        }

        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        writeError(response, 401, message);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        writeError(response, 403, message);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(status, message)));
    }
}

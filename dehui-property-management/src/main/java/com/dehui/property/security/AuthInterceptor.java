package com.dehui.property.security;

import com.dehui.property.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = "authPrincipal";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (isPublicRequest(request)) {
            return true;
        }

        String token = request.getHeader("Authorization");
        return tokenService.readToken(token)
                .map(principal -> {
                    request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
                    return true;
                })
                .orElseGet(() -> {
                    writeError(response, HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
                    return false;
                });
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        return "OPTIONS".equalsIgnoreCase(method)
                || uri.equals("/api/ping")
                || uri.equals("/api/system/login")
                || uri.equals("/api/mobile/auth/wechat-login")
                || uri.startsWith("/api/mobile/announcements")
                || uri.startsWith("/api/mobile/investment")
                || uri.startsWith("/api/files/public/")
                || uri.startsWith("/api/error");
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) {
        try {
            response.setStatus(status.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(status.value(), message)));
        } catch (Exception ignored) {
            response.setStatus(status.value());
        }
    }
}

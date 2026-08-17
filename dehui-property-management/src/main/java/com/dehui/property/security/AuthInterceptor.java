package com.dehui.property.security;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.mobile.controller.MobileApiSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = "authPrincipal";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldWorkspaceRegistry customFieldWorkspaceRegistry;

    @Autowired
    public AuthInterceptor(
            TokenService tokenService,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            CustomFieldWorkspaceRegistry customFieldWorkspaceRegistry
    ) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldWorkspaceRegistry = customFieldWorkspaceRegistry;
    }

    public AuthInterceptor(TokenService tokenService, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this(tokenService, objectMapper, jdbcTemplate, new CustomFieldWorkspaceRegistry());
    }

    AuthInterceptor(TokenService tokenService, ObjectMapper objectMapper) {
        this(tokenService, objectMapper, null);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (isPublicRequest(request)) {
            tokenService.readToken(request.getHeader("Authorization"))
                    .flatMap(this::latestPrincipal)
                    .ifPresent(principal -> request.setAttribute(PRINCIPAL_ATTRIBUTE, principal));
            return true;
        }

        String token = request.getHeader("Authorization");
        Optional<AuthPrincipal> tokenPrincipal = tokenService.readToken(token);
        if (tokenPrincipal.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "\u672a\u767b\u5f55\u6216\u767b\u5f55\u5df2\u8fc7\u671f");
            return false;
        }
        Optional<AuthPrincipal> currentPrincipal = latestPrincipal(tokenPrincipal.get());
        if (currentPrincipal.isEmpty()) {
            if (isStaffPrincipal(tokenPrincipal.get())) {
                tokenService.revoke(token);
            }
            writeError(response, HttpStatus.UNAUTHORIZED, "\u79fb\u52a8\u7aef\u8d26\u53f7\u5df2\u5931\u6548\u6216\u5df2\u505c\u7528");
            return false;
        }
        if (isStaffPrincipal(tokenPrincipal.get()) && !isStaffPrincipal(currentPrincipal.get())) {
            tokenService.revoke(token);
            writeError(response, HttpStatus.UNAUTHORIZED, "物业员工身份已失效，请重新验证手机号");
            return false;
        }

        AuthPrincipal principal = currentPrincipal.get();
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        if (!isAuthorized(request, principal)) {
            writeError(response, HttpStatus.FORBIDDEN, "\u65e0\u6743\u8bbf\u95ee\u8be5\u8d44\u6e90");
            return false;
        }
        return true;
    }

    private Optional<AuthPrincipal> latestPrincipal(AuthPrincipal principal) {
        if (jdbcTemplate == null) {
            return Optional.of(principal);
        }
        if (isMobilePrincipal(principal)) {
            return MobileApiSupport.currentPrincipal(jdbcTemplate, principal.userId());
        }
        if (!"SYSTEM".equals(principal.userType())) {
            return Optional.of(principal);
        }
        if (!isActiveSystemUser(principal.userId())) {
            return Optional.empty();
        }
        List<String> roles = latestRoles(principal.userId());
        List<String> permissions = latestPermissions(principal.userId());
        return Optional.of(new AuthPrincipal(
                principal.userId(),
                principal.username(),
                principal.userType(),
                principal.tenantId(),
                roles,
                permissions
        ));
    }

    private List<String> latestRoles(Long userId) {
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

    private boolean isActiveSystemUser(Long userId) {
        List<Long> ids = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM sys_user
                WHERE id = ?
                  AND deleted = 0
                  AND status = 'ENABLED'
                  AND web_login_enabled = 1
                LIMIT 1
                """,
                Long.class,
                userId
        );
        return !ids.isEmpty();
    }

    private List<String> latestPermissions(Long userId) {
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

    private boolean isPublicRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        return "OPTIONS".equalsIgnoreCase(method)
                || uri.equals("/api/ping")
                || uri.equals("/api/system/login")
                || (uri.equals("/api/mobile/auth/wechat-login") && "POST".equalsIgnoreCase(method))
                || (uri.startsWith("/api/mobile/announcements") && "GET".equalsIgnoreCase(method))
                || (uri.startsWith("/api/mobile/public/meeting-rooms") && "GET".equalsIgnoreCase(method))
                || (uri.equals("/api/mobile/public/visitors") && "POST".equalsIgnoreCase(method))
                || (uri.equals("/api/mobile/public/visitors/history") && "POST".equalsIgnoreCase(method))
                || (uri.matches("^/api/mobile/public/visitors/[^/]+/qr$")
                    && "GET".equalsIgnoreCase(method))
                || isPublicMobileInvestmentRequest(uri, method)
                || uri.startsWith("/api/miniapp/investment-content")
                || uri.startsWith("/files/public/")
                || uri.startsWith("/api/files/public/")
                || uri.startsWith("/api/error");
    }

    private boolean isPublicMobileInvestmentRequest(String uri, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            return uri.equals("/api/mobile/investment/overview")
                    || uri.equals("/api/mobile/investment/showcase")
                    || uri.equals("/api/mobile/investment-content")
                    || uri.equals("/api/mobile/investment/rooms")
                    || uri.equals("/api/mobile/investment/listings")
                    || uri.matches("^/api/mobile/investment/rooms/[^/]+$")
                    || uri.matches("^/api/mobile/investment/listings/[^/]+$");
        }
        return false;
    }

    private boolean isAuthorized(HttpServletRequest request, AuthPrincipal principal) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/mobile/")) {
            return isAuthorizedMobileRequest(uri, request.getMethod(), principal);
        }
        if (principal.roles().contains("SUPER_ADMIN")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod())
                && uri.matches("^/api/tenants/[^/]+/archive/bills(?:/.*)?$")) {
            return hasPermission(principal.permissions(), "tenant:view")
                    && hasPermission(principal.permissions(), "bill:view");
        }
        if (uri.startsWith("/api/miniapp/") || uri.startsWith("/api/auth/")) {
            return true;
        }
        List<String> required = requiredPermissions(uri, request.getMethod());
        if (required.isEmpty()) {
            return true;
        }
        return required.stream().anyMatch(permission -> hasPermission(principal.permissions(), permission));
    }

    private boolean isAuthorizedMobileRequest(String uri, String method, AuthPrincipal principal) {
        if (!isMobilePrincipal(principal)) {
            return false;
        }
        if (uri.startsWith("/api/mobile/auth/")
                || uri.equals("/api/mobile/me")
                || uri.equals("/api/mobile/mine/summary")) {
            return true;
        }
        if (uri.startsWith("/api/mobile/public/meeting-rooms")) {
            return "GET".equalsIgnoreCase(method)
                    && hasPermission(principal.permissions(), "meeting.public.view");
        }
        if (uri.equals("/api/mobile/public/meeting-bookings")) {
            return "POST".equalsIgnoreCase(method)
                    && hasPermission(principal.permissions(), "meeting.guest.book");
        }
        if (uri.startsWith("/api/mobile/my-business/")) {
            if (!"TENANT".equals(principal.userType()) || principal.tenantId() == null) {
                return false;
            }
            return uri.startsWith("/api/mobile/my-business/rooms")
                    ? hasPermission(principal.permissions(), "contract.view")
                    : uri.equals("/api/mobile/my-business/summary");
        }
        if (uri.startsWith("/api/mobile/investment/")) {
            boolean appointmentRequest = uri.equals("/api/mobile/investment/leads")
                    || uri.equals("/api/mobile/investment/leads/mine")
                    || uri.equals("/api/mobile/investment/appointments")
                    || uri.equals("/api/mobile/investment/appointments/mine")
                    || uri.matches("^/api/mobile/investment/appointments/[^/]+$");
            return appointmentRequest
                    && hasPermission(principal.permissions(), "investment.booking.create");
        }
        if (uri.startsWith("/api/mobile/files/") && "GET".equalsIgnoreCase(method)) {
            return "TENANT".equals(principal.userType()) || "STAFF".equals(principal.userType());
        }
        if (uri.equals("/api/mobile/files") && "POST".equalsIgnoreCase(method)) {
            return ("TENANT".equals(principal.userType()) && hasAnyMobileCapability(principal,
                    "workorder.create", "visitor.create"))
                    || ("STAFF".equals(principal.userType()) && hasAnyMobileCapability(principal,
                    "inspection.staff.create", "inspection.staff.update",
                    "workorder.staff.start", "workorder.staff.complete"));
        }
        if (uri.equals("/api/mobile/staff/workbench") && "GET".equalsIgnoreCase(method)) {
            return "STAFF".equals(principal.userType());
        }
        if (uri.equals("/api/mobile/staff/files") && "POST".equalsIgnoreCase(method)) {
            return "STAFF".equals(principal.userType()) && hasAnyMobileCapability(principal,
                    "inspection.staff.create",
                    "inspection.staff.update",
                    "workorder.staff.start",
                    "workorder.staff.complete",
                    "energy.staff.reading.create");
        }
        if (uri.equals("/api/mobile/staff/inspection-types") && "GET".equalsIgnoreCase(method)) {
            return "STAFF".equals(principal.userType())
                    && hasPermission(principal.permissions(), "inspection.staff.view");
        }
        if (uri.equals("/api/mobile/staff/inspection-options") && "GET".equalsIgnoreCase(method)) {
            return "STAFF".equals(principal.userType())
                    && hasPermission(principal.permissions(), "inspection.staff.view");
        }
        if (uri.startsWith("/api/mobile/staff/inspections")) {
            if ("GET".equalsIgnoreCase(method)) {
                return "STAFF".equals(principal.userType())
                        && hasPermission(principal.permissions(), "inspection.staff.view");
            }
            String capability = "POST".equalsIgnoreCase(method)
                    && uri.equals("/api/mobile/staff/inspections")
                    ? "inspection.staff.create"
                    : "inspection.staff.update";
            return "STAFF".equals(principal.userType())
                    && hasPermission(principal.permissions(), capability);
        }
        if (uri.startsWith("/api/mobile/staff/energy")) {
            String capability = "POST".equalsIgnoreCase(method)
                    ? "energy.staff.reading.create"
                    : "energy.staff.view";
            return "STAFF".equals(principal.userType())
                    && hasPermission(principal.permissions(), capability);
        }
        if (uri.startsWith("/api/mobile/staff/meetings")) {
            return "STAFF".equals(principal.userType())
                    && ("GET".equalsIgnoreCase(method)
                    ? hasPermission(principal.permissions(), "meeting.staff.view")
                    : hasAnyMobileCapability(principal,
                            "meeting.staff.manage",
                            "meeting.staff.confirm",
                            "meeting.staff.cancel",
                            "meeting.staff.complete"));
        }
        if (uri.startsWith("/api/mobile/staff/visitors")) {
            return "STAFF".equals(principal.userType())
                    && ("GET".equalsIgnoreCase(method)
                    ? hasPermission(principal.permissions(), "visitor.staff.view")
                    : hasPermission(principal.permissions(), "visitor.staff.verify"));
        }
        if (uri.startsWith("/api/mobile/staff/investment-leads")) {
            return "STAFF".equals(principal.userType())
                    && ("GET".equalsIgnoreCase(method)
                    ? hasPermission(principal.permissions(), "investment.lead.staff.view")
                    : hasPermission(principal.permissions(), "investment.lead.staff.update"));
        }
        if (uri.matches("^/api/mobile/staff/workorders/[^/]+/records$")
                && "POST".equalsIgnoreCase(method)) {
            return "STAFF".equals(principal.userType())
                    && hasPermission(principal.permissions(), "workorder.staff.start");
        }
        if (uri.startsWith("/api/mobile/energy/")) {
            return hasPermission(principal.permissions(), "energy.company.view")
                    || hasPermission(principal.permissions(), "energy.staff.view");
        }
        if (uri.equals("/api/mobile/parking/company") || uri.matches("^/api/mobile/parking/[^/]+$")) {
            return hasPermission(principal.permissions(), "parking.company.view")
                    || hasPermission(principal.permissions(), "parking.mine.view")
                    || hasPermission(principal.permissions(), "parking.staff.view");
        }
        if (uri.equals("/api/mobile/parking/mine")) {
            return hasPermission(principal.permissions(), "parking.mine.view");
        }
        if (uri.startsWith("/api/mobile/module-fields/") && "GET".equalsIgnoreCase(method)) {
            String moduleKey = uri.substring("/api/mobile/module-fields/".length()).split("/")[0];
            return switch (moduleKey) {
                case "workorder", "work-order", "workorders" ->
                        hasPermission(principal.permissions(), "workorder.view")
                                || hasPermission(principal.permissions(), "workorder.staff.view");
                case "visitor", "visitors" ->
                        hasPermission(principal.permissions(), "visitor.create")
                                || hasPermission(principal.permissions(), "visitor.staff.view");
                case "inspection", "inspections" ->
                        hasPermission(principal.permissions(), "inspection.staff.view");
                case "rooms", "energy-meters", "energy-records", "energy-readings" ->
                        hasPermission(principal.permissions(), "energy.company.view")
                                || hasPermission(principal.permissions(), "energy.staff.view")
                                || hasPermission(principal.permissions(), "contract.view");
                case "bills" -> hasPermission(principal.permissions(), "bill.view");
                case "contracts" -> hasPermission(principal.permissions(), "contract.view");
                case "parking-spaces", "parking-assignments" ->
                        hasPermission(principal.permissions(), "parking.company.view")
                                || hasPermission(principal.permissions(), "parking.mine.view")
                                || hasPermission(principal.permissions(), "parking.staff.view");
                case "meeting-rooms", "meeting-bookings" ->
                        hasPermission(principal.permissions(), "meeting.book")
                                || hasPermission(principal.permissions(), "meeting.guest.book")
                                || hasPermission(principal.permissions(), "meeting.staff.view");
                case "investment-contents", "investment-listings", "investment-leads" ->
                        hasPermission(principal.permissions(), "investment.view")
                                || hasPermission(principal.permissions(), "investment.booking.create")
                                || hasPermission(principal.permissions(), "investment.lead.staff.view");
                case "announcements" ->
                        hasPermission(principal.permissions(), "announcement.view");
                default -> false;
            };
        }
        if (uri.startsWith("/api/mobile/meetings") || uri.startsWith("/api/mobile/meeting-rooms")) {
            if ("GET".equalsIgnoreCase(method)) {
                return hasPermission(principal.permissions(), "meeting.book")
                        || hasPermission(principal.permissions(), "meeting.staff.view");
            }
            return hasPermission(principal.permissions(), "meeting.book");
        }
        if (uri.startsWith("/api/mobile/bills/" ) || uri.equals("/api/mobile/bills")) {
            return "TENANT".equals(principal.userType())
                    && principal.tenantId() != null
                    && hasPermission(principal.permissions(), "bill.view");
        }
        if (uri.startsWith("/api/mobile/contracts/" ) || uri.equals("/api/mobile/contracts")) {
            return "TENANT".equals(principal.userType())
                    && principal.tenantId() != null
                    && hasPermission(principal.permissions(), "contract.view");
        }
        if (uri.startsWith("/api/mobile/workorders")) {
            if (uri.matches("^/api/mobile/workorders/[^/]+/start$")) {
                return hasPermission(principal.permissions(), "workorder.staff.start");
            }
            if (uri.matches("^/api/mobile/workorders/[^/]+/complete$")) {
                return hasPermission(principal.permissions(), "workorder.staff.complete");
            }
            if (uri.contains("/notifications/")) {
                return hasPermission(principal.permissions(), "workorder.staff.view");
            }
            if ("GET".equalsIgnoreCase(method)) {
                return hasPermission(principal.permissions(), "workorder.view")
                        || hasPermission(principal.permissions(), "workorder.staff.view");
            }
            if (uri.equals("/api/mobile/workorders")) {
                return hasPermission(principal.permissions(), "workorder.create");
            }
            if (uri.matches("^/api/mobile/workorders/[^/]+/images$")) {
                return hasPermission(principal.permissions(), "workorder.create")
                        || hasPermission(principal.permissions(), "workorder.staff.complete");
            }
            return hasPermission(principal.permissions(), "workorder.view");
        }
        if (uri.startsWith("/api/mobile/visitors")) {
            return "TENANT".equals(principal.userType())
                    && hasPermission(principal.permissions(), "visitor.create");
        }
        return false;
    }

    private boolean hasAnyMobileCapability(AuthPrincipal principal, String... capabilities) {
        for (String capability : capabilities) {
            if (hasPermission(principal.permissions(), capability)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMobilePrincipal(AuthPrincipal principal) {
        if (principal == null) {
            return false;
        }
        return "PUBLIC".equals(principal.userType())
                || "TENANT".equals(principal.userType())
                || "STAFF".equals(principal.userType())
                || "INTERNAL".equals(principal.userType());
    }

    private boolean isStaffPrincipal(AuthPrincipal principal) {
        return principal != null
                && ("STAFF".equals(principal.userType()) || "INTERNAL".equals(principal.userType()));
    }

    private List<String> requiredPermissions(String uri, String method) {
        String path = uri.startsWith("/api") ? uri.substring(4) : uri;
        if (path.startsWith("/system/users")) return List.of("system:user:" + userAction(method, path));
        if (path.startsWith("/mobile-users")) return List.of("mobile:" + ("GET".equalsIgnoreCase(method) ? "view" : "update"));
        if (path.startsWith("/system/roles")) return List.of("system:role:" + roleAction(method, path));
        if (path.startsWith("/system/menus")) return List.of("system:menu:view", "system:role:assign-permission");
        if (path.startsWith("/system/permissions")) return List.of("system:permission:view", "system:role:assign-permission");
        if (path.startsWith("/custom-fields")
                || path.matches("^/workspaces/[^/]+/(?:custom-fields|records/.+/custom-fields|custom-field-values).*$")) {
            return customFieldPermissions(path, method);
        }
        if (path.startsWith("/module-fields/")) {
            String workspaceKey = path.substring("/module-fields/".length()).split("/")[0];
            if (!customFieldWorkspaceRegistry.supports(workspaceKey)) {
                return List.of(CustomFieldWorkspaceRegistry.VIEW_PERMISSION);
            }
            return List.of(customFieldWorkspaceRegistry.require(workspaceKey).viewPermission());
        }
        if (path.startsWith("/payments")) return List.of("bill:view");
        if (path.startsWith("/invoices")) return List.of("bill:view");
        if (path.startsWith("/finance/dashboard")) return List.of("finance:dashboard:view");
        if (path.startsWith("/logs/operations")) return List.of("system:user:view");
        if (path.startsWith("/logs/logins")) return List.of("system:user:view");
        if (path.startsWith("/assets")) return List.of("asset:" + action(method));
        if (path.startsWith("/feerules")) return List.of("feerule:" + feeRuleAction(method, path));
        if (path.startsWith("/fee-rules")) return List.of(feeRulePermission(path, method));
        if (path.startsWith("/billing-plans")) return List.of(billingPlanPermission(path, method));
        if (path.matches("^/buildings/[^/]+/floors/[^/]+/rooms.*")) return List.of("room:view");
        if (path.matches("^/buildings/[^/]+/floors.*")) return List.of("floor:view");
        if (path.startsWith("/buildings")) return List.of("building:" + action(method));
        if (path.startsWith("/floors")) return List.of("floor:" + action(method));
        if (path.startsWith("/rooms")) return List.of(roomPermission(method, path));
        if (path.startsWith("/equipments") || path.startsWith("/equipment")) return List.of("asset:" + action(method));
        if (path.matches("^/tenants/[^/]+/archive/bills(?:/.*)?$")
                && "GET".equalsIgnoreCase(method)) {
            return List.of("tenant:view", "bill:view");
        }
        if (path.startsWith("/tenant")) return List.of("tenant:" + action(method));
        if (path.startsWith("/tenants")) return List.of("tenant:" + action(method));
        if (path.startsWith("/contracts")) return List.of(contractPermission(path, method));
        if (path.startsWith("/leases")) return List.of(leasePermission(path, method));
        if (path.startsWith("/bills")) {
            if (path.startsWith("/bills/charge-bases")) {
                return List.of("GET".equalsIgnoreCase(method)
                        ? "asset-charge-basis:view"
                        : "asset-charge-basis:generate");
            }
            if (path.matches("^/bills/\\d+/invoice") && "POST".equalsIgnoreCase(method)) {
                return List.of("finance:invoice:upload");
            }
            if (path.matches("^/bills/\\d+/invoice") && "DELETE".equalsIgnoreCase(method)) {
                return List.of("finance:invoice:void");
            }
            return List.of("bill:" + billAction(method, path));
        }
        if (path.startsWith("/investment") || path.startsWith("/admin/investment")) return List.of(investmentPermission(path, method));
        if (path.startsWith("/parking/bills")) return List.of("parking-bill:" + parkingBillAction(method, path));
        if (path.startsWith("/parking")) return List.of("parking:" + action(method));
        if (path.startsWith("/meeting")) return List.of(meetingPermission(path, method));
        if (path.startsWith("/inspection-types")) return List.of(inspectionTypePermission(method));
        if (path.startsWith("/workorders")) return List.of("workorder:" + workorderAction(method, path));
        if (path.startsWith("/inspections")) return List.of("inspection:" + inspectionAction(method, path));
        if (path.startsWith("/visitors")) return List.of("visitor:" + action(method));
        if (path.startsWith("/announcements")) {
            if (path.matches("^/announcements/\\d+/(publish|disable)$") && "POST".equalsIgnoreCase(method)) {
                return List.of("announcement:update");
            }
            return List.of("announcement:" + action(method));
        }
        if (path.startsWith("/energy")) return List.of(energyPermission(path, method));
        if (path.startsWith("/ai")) return List.of("ai:view");
        return List.of();
    }

    private boolean hasPermission(List<String> permissions, String required) {
        if (permissions.contains(required)) return true;
        int colonIndex = required.indexOf(':');
        if (colonIndex > 0 && permissions.contains(required.substring(0, colonIndex) + ":*")) return true;
        return permissions.contains("*");
    }

    private String action(String method) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "GET" -> "view";
            case "POST" -> "create";
            case "PUT", "PATCH" -> "update";
            case "DELETE" -> "delete";
            default -> "view";
        };
    }

    private String roleAction(String method, String path) {
        if (path.contains("/menus") || path.contains("/permissions") || path.contains("/access")) return "assign-permission";
        return action(method);
    }

    private String userAction(String method, String path) {
        if (path.contains("/roles")) return "assign-role";
        if (path.contains("/reset-password")) return "reset-password";
        if (path.contains("/status")) return "disable";
        if (path.contains("/mobile-binding")) return "update";
        return action(method);
    }

    private String billAction(String method, String path) {
        if (path.contains("/approve") || path.contains("/reject") || path.contains("/audit")) return "audit";
        if (path.contains("/submit")) return "submit";
        if (path.matches("^/bills/\\d+/record/check$") && "POST".equalsIgnoreCase(method)) return "delete";
        if (path.matches("^/bills/\\d+/record$") && "DELETE".equalsIgnoreCase(method)) return "delete";
        if (path.contains("/void") || "DELETE".equalsIgnoreCase(method)) return "void";
        if (path.contains("/adjustment")) return "adjustment";
        if (path.contains("/invoice") && "POST".equalsIgnoreCase(method)) {
            return "finance:invoice:upload";
        }
        if ((path.contains("/pay") || path.contains("/payment")) && !"GET".equalsIgnoreCase(method)) return "pay";
        if ("POST".equalsIgnoreCase(method)) return "add";
        return action(method);
    }

    private String feeRuleAction(String method, String path) {
        if (path.contains("/generate-bill")) return "generate";
        if ("POST".equalsIgnoreCase(method)) return "add";
        return action(method);
    }

    private String roomPermission(String method, String path) {
        if (path.contains("/lease")) return "lease:checkin";
        if (path.contains("/checkout")) return "lease:checkout";
        return "room:" + action(method);
    }

    private String feeRulePermission(String path, String method) {
        if (path.matches("^/fee-rules/[^/]+/activate$")) return "fee-rule:activate";
        if (path.matches("^/fee-rules/[^/]+/disable$")) return "fee-rule:update";
        return "fee-rule:" + action(method);
    }

    private String billingPlanPermission(String path, String method) {
        if (path.matches("^/billing-plans/[^/]+/terminate$")) return "billing-plan:terminate";
        if (path.matches("^/billing-plans/[^/]+/activate$")) return "billing-plan:update";
        return "billing-plan:" + action(method);
    }

    private String contractPermission(String path, String method) {
        if (path.matches("^/contracts/[^/]+/approve$")) return "contract:approve";
        if (path.matches("^/contracts/[^/]+/(activate|reactivate)$")) return "contract:activate";
        if (path.matches("^/contracts/[^/]+/terminate$")) return "contract:terminate";
        if (path.matches("^/contracts/[^/]+/cancel$")) return "contract:cancel";
        if (path.matches("^/contracts/[^/]+/check-in$")) return "lease:checkin";
        if (path.matches("^/contracts/[^/]+/(submit|file)$")) return "contract:update";
        return "contract:" + action(method);
    }

    private String leasePermission(String path, String method) {
        if (path.matches("^/leases/[^/]+/check-in$")) return "lease:checkin";
        if (path.matches("^/leases/[^/]+/check-out$")) return "lease:checkout";
        if (path.matches("^/leases/[^/]+/renew$")) return "lease:renew";
        if (path.matches("^/leases/[^/]+/terminate$")) return "lease:terminate";
        return "lease:" + action(method);
    }

    private List<String> customFieldPermissions(String path, String method) {
        String workspaceKey = "";
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            workspaceKey = parts[2];
        }
        if (!customFieldWorkspaceRegistry.supports(workspaceKey)) {
            return List.of(CustomFieldWorkspaceRegistry.MANAGE_PERMISSION);
        }
        var workspace = customFieldWorkspaceRegistry.require(workspaceKey);
        if ("GET".equalsIgnoreCase(method)) return List.of(workspace.viewPermission());

        boolean valueMutation = path.contains("/records/") || path.contains("/custom-field-values");
        if (valueMutation) return workspace.valueUpdatePermissions().stream().toList();

        // Application policy additionally requires the workspace update permission.
        return List.of(CustomFieldWorkspaceRegistry.MANAGE_PERMISSION);
    }

    private String investmentPermission(String path, String method) {
        String normalized = path.startsWith("/admin/investment") ? path.substring("/admin".length()) : path;
        if (normalized.startsWith("/investment/contents")) {
            if (normalized.contains("/publish") || normalized.contains("/disable")) {
                return "investment-content:publish";
            }
            return "investment-content:" + action(method);
        }
        if (normalized.startsWith("/investment/listings/images")) {
            return "GET".equalsIgnoreCase(method) ? "investment-listing:view" : "investment-listing:image";
        }
        if (normalized.startsWith("/investment/listings")) {
            if (normalized.contains("/images")) {
                return "GET".equalsIgnoreCase(method) ? "investment-listing:view" : "investment-listing:image";
            }
            if (normalized.contains("/publish") || normalized.contains("/offline")) {
                return "investment-listing:publish";
            }
            return "investment-listing:" + action(method);
        }
        if (normalized.startsWith("/investment/rooms")) {
            return "investment-listing:view";
        }
        if (normalized.startsWith("/investment/leads")) {
            if (normalized.contains("/status")) {
                return "investment-lead:follow";
            }
            if (normalized.contains("/convert-tenant") || normalized.contains("/convert-contract")) {
                return "investment-lead:convert";
            }
            return "investment-lead:" + action(method);
        }
        return "investment-content:view";
    }

    private String meetingPermission(String path, String method) {
        if (path.startsWith("/meeting-rooms")) {
            return "meeting-room:" + action(method);
        }
        if (path.startsWith("/meeting-bookings")) {
            if (path.contains("/confirm") || path.contains("/reject")) return "meeting-booking:confirm";
            if (path.contains("/cancel")) return "meeting-booking:cancel";
            if (path.contains("/complete")) return "meeting-booking:complete";
            return "meeting-booking:" + action(method);
        }
        if (path.startsWith("/meeting-bills")) {
            return "meeting-booking:view";
        }
        return "meeting-booking:view";
    }

    private String parkingBillAction(String method, String path) {
        if (path.contains("/pay")) return "pay";
        if (path.contains("/void")) return "update";
        if ("POST".equalsIgnoreCase(method)) return "add";
        return action(method);
    }

    private String workorderAction(String method, String path) {
        if (path.contains("/assign")) return "assign";
        if (path.contains("/complete")) return "complete";
        if (path.contains("/close")) return "close";
        return action(method);
    }

    private String inspectionAction(String method, String path) {
        if (path.contains("/attachments/")) {
            return "update";
        }
        if (path.contains("/close")) return "update";
        return action(method);
    }

    private String inspectionTypePermission(String method) {
        if ("GET".equalsIgnoreCase(method)) {
            return "inspection:view";
        }
        return "inspection:update";
    }

    private String energyPermission(String path, String method) {
        if (path.startsWith("/energy/meters")) {
            return "energy:meter:" + action(method);
        }
        if (path.startsWith("/energy/price-settings")) {
            return "GET".equalsIgnoreCase(method) ? "energy:view" : "energy:update";
        }
        if (path.startsWith("/energy/stats")) {
            return "energy:view";
        }
        if (path.startsWith("/energy/readings")) {
            if ("GET".equalsIgnoreCase(method)) return "energy:view";
            if (path.contains("/withdraw-bill")
                    || path.contains("/mark-posted")
                    || path.contains("/anomaly-status")) {
                return "energy:reading:update";
            }
            if (path.contains("/generate-bill")
                    || path.contains("/batch-generate-bills")
                    || path.contains("/monthly-tasks")
                    || path.contains("/import")) {
                return "energy:create";
            }
            if ("DELETE".equalsIgnoreCase(method)) return "energy:reading:delete";
            if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) return "energy:reading:update";
            return "energy:create";
        }
        return "energy:" + action(method);
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

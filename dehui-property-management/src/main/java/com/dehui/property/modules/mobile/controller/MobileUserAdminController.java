package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class MobileUserAdminController {

    private static final Map<String, String> MOBILE_USER_ORDER_FIELDS = Map.ofEntries(
            Map.entry("code", "mu.code"),
            Map.entry("phone", "mu.phone"),
            Map.entry("nickname", "mu.nickname"),
            Map.entry("realName", "mu.real_name"),
            Map.entry("userType", "mu.user_type"),
            Map.entry("tenantName", "t.name"),
            Map.entry("tenantContactName", "tc.name"),
            Map.entry("boundSysRealName", "su.real_name"),
            Map.entry("status", "mu.status"),
            Map.entry("createdAt", "mu.created_at"),
            Map.entry("updatedAt", "mu.updated_at")
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;
    private final MobileIdentityService mobileIdentityService;

    public MobileUserAdminController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null, new MobileIdentityService(jdbcTemplate));
    }

    @GetMapping("/mobile-users")
    public ApiResponse<Map<String, Object>> mobileUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String tenantContactName,
            @RequestParam(required = false) String boundSysRealName,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        int pageIndex = Math.max(page == null ? 0 : page, 0);
        int pageSize = Math.min(Math.max(size == null ? 20 : size, 1), 200);
        int offset = pageIndex * pageSize;

        StringBuilder where = new StringBuilder(" WHERE mu.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        appendFilters(
                where,
                args,
                keyword,
                userType,
                status,
                code,
                phone,
                nickname,
                realName,
                tenantName,
                tenantContactName,
                boundSysRealName
        );
        CompiledQuery customQuery = compileCustomQuery(customFilters, groupBy, sorts, sortField, sortDirection);
        if (customQuery != null && customQuery.hasPredicates()) {
            where.append(" AND ").append(customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }

        String joins = """
                LEFT JOIN mobile_user_bind current_bind
                  ON current_bind.id = mu.current_bind_id
                 AND current_bind.mobile_user_id = mu.id
                 AND current_bind.status = 'ENABLED'
                 AND current_bind.deleted = 0
                LEFT JOIN mobile_user_bind tb
                  ON tb.id = current_bind.id
                 AND tb.bind_type = 'TENANT'
                LEFT JOIN tenant t ON t.id = tb.tenant_id AND t.deleted = 0
                LEFT JOIN tenant_contact tc ON tc.id = tb.tenant_contact_id AND tc.deleted = 0
                LEFT JOIN mobile_user_bind ib
                  ON ib.id = current_bind.id
                 AND ib.bind_type = 'INTERNAL'
                LEFT JOIN sys_user su ON su.id = ib.sys_user_id AND su.deleted = 0
                """;

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mobile_user mu " + joins + where,
                Long.class,
                args.toArray()
        );

        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(
                        MOBILE_USER_ORDER_FIELDS, groupBy, sorts, sortField, sortDirection,
                        "mu.updated_at", "mu.id"
                )
                : String.join(", ", customQuery.orderExpressions());

        List<Object> listArgs = new ArrayList<>(args);
        if (customQuery != null) listArgs.addAll(customQuery.orderArguments());
        listArgs.add(pageSize);
        listArgs.add(offset);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                """
                SELECT mu.id,
                       mu.code,
                       mu.nickname,
                       mu.real_name AS realName,
                       mu.phone,
                       mu.phone_verified_at AS phoneVerifiedAt,
                       mu.phone_verification_source AS phoneVerificationSource,
                       mu.user_type AS userType,
                       mu.status,
                       mu.created_at AS createdAt,
                       mu.updated_at AS updatedAt,
                       mu.last_login_at AS lastLoginAt,
                       tb.tenant_id AS tenantId,
                       t.name AS tenantName,
                       tb.tenant_contact_id AS tenantContactId,
                       tc.name AS tenantContactName,
                       tc.phone AS tenantContactPhone,
                       CASE
                         WHEN tc.is_primary = 1 OR tc.role = 'RESPONSIBLE' THEN 'RESPONSIBLE'
                         WHEN tc.id IS NOT NULL THEN 'EMPLOYEE'
                         ELSE NULL
                       END AS contactRole,
                       ib.sys_user_id AS boundSysUserId,
                       su.username AS boundSysUsername,
                       su.real_name AS boundSysRealName,
                       su.phone AS boundSysPhone,
                       su.status AS boundSysStatus,
                       su.mobile_enabled AS boundSysMobileEnabled,
                       (
                         SELECT GROUP_CONCAT(DISTINCT role_record.name ORDER BY role_record.id SEPARATOR '、')
                         FROM sys_user_role user_role
                         INNER JOIN sys_role role_record
                           ON role_record.id = user_role.role_id
                          AND role_record.deleted = 0
                          AND role_record.status = 'ENABLED'
                         WHERE user_role.user_id = su.id
                           AND user_role.deleted = 0
                           AND user_role.status = 'ENABLED'
                       ) AS backendRoleSummary,
                       CASE
                         WHEN current_bind.bind_type = 'INTERNAL'
                           AND mu.phone_verified_at IS NOT NULL
                           AND mu.phone_verification_source = 'WECHAT_API'
                           AND su.status IN ('ACTIVE', 'ENABLED')
                           AND su.mobile_enabled = 1
                           AND %s = %s
                           AND EXISTS (
                             SELECT 1
                             FROM sys_user_role mobile_user_role
                             INNER JOIN sys_role mobile_role
                               ON mobile_role.id = mobile_user_role.role_id
                              AND mobile_role.deleted = 0
                              AND mobile_role.status IN ('ACTIVE', 'ENABLED')
                             INNER JOIN sys_role_permission mobile_role_permission
                               ON mobile_role_permission.role_id = mobile_role.id
                              AND mobile_role_permission.deleted = 0
                              AND mobile_role_permission.status IN ('ACTIVE', 'ENABLED')
                             INNER JOIN sys_permission mobile_permission
                               ON mobile_permission.id = mobile_role_permission.permission_id
                              AND mobile_permission.deleted = 0
                              AND mobile_permission.status IN ('ACTIVE', 'ENABLED')
                             WHERE mobile_user_role.user_id = su.id
                               AND mobile_user_role.deleted = 0
                               AND mobile_user_role.status IN ('ACTIVE', 'ENABLED')
                               AND mobile_permission.code IN ('staff-mobile:access', 'staff-mobile:*', '*')
                           ) THEN 'ENABLED'
                         WHEN current_bind.bind_type = 'INTERNAL' THEN 'DISABLED'
                         ELSE 'NOT_APPLICABLE'
                       END AS mobileWorkbenchState,
                       CASE
                         WHEN mu.status IN ('DISABLED', 'INACTIVE') THEN 'DISABLED'
                         WHEN mu.phone IS NULL OR mu.phone = '' THEN 'UNVERIFIED'
                         WHEN mu.phone_verified_at IS NULL THEN 'REVERIFY_REQUIRED'
                         WHEN current_bind.id IS NULL AND (
                           SELECT COUNT(*)
                           FROM mobile_user_bind pending_bind
                           WHERE pending_bind.mobile_user_id = mu.id
                             AND pending_bind.status = 'ENABLED'
                             AND pending_bind.deleted = 0
                         ) > 1 THEN 'PENDING_SELECTION'
                         WHEN current_bind.id IS NULL THEN 'UNMATCHED'
                         ELSE 'BOUND'
                       END AS bindingState,
                       (
                         SELECT COUNT(*)
                         FROM mobile_user_bind available_bind
                         WHERE available_bind.mobile_user_id = mu.id
                           AND available_bind.status = 'ENABLED'
                           AND available_bind.deleted = 0
                       ) AS availableIdentityCount
                FROM mobile_user mu
                """.formatted(
                        normalizedPhoneSql("mu.phone"),
                        normalizedPhoneSql("su.phone")
                ) + joins + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                listArgs.toArray()
        );
        if (customFieldProjectionService != null) {
            records = customFieldProjectionService.attach("mobile-users", records);
        }
        records.forEach(record -> {
            record.put("verifiedPhone", maskPhone(record.get("phone")));
            if (record.get("boundSysUserId") != null
                    && !"ENABLED".equals(String.valueOf(record.get("mobileWorkbenchState")))) {
                boolean requiresWechatVerification =
                        record.get("phoneVerifiedAt") == null
                                || !"WECHAT_API".equalsIgnoreCase(
                                        String.valueOf(record.get("phoneVerificationSource")));
                record.put(
                        "bindingState",
                        requiresWechatVerification ? "REVERIFY_REQUIRED" : "INVALID_BINDING"
                );
            }
            record.remove("phone");
        });

        Map<String, Object> pageData = new LinkedHashMap<>();
        pageData.put("records", records);
        pageData.put("total", total == null ? 0L : total);
        pageData.put("page", pageIndex);
        pageData.put("size", pageSize);
        return ApiResponse.success(pageData);
    }

    public ApiResponse<Map<String, Object>> mobileUsers(
            String keyword,
            String userType,
            String status,
            String code,
            String phone,
            String nickname,
            String realName,
            String tenantName,
            String tenantContactName,
            String boundSysRealName,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer size) {
        return mobileUsers(
                keyword, userType, status, code, phone, nickname, realName, tenantName,
                tenantContactName, boundSysRealName, null, sortField, sortDirection,
                sorts, groupBy, page, size
        );
    }

    public ApiResponse<Map<String, Object>> mobileUsers(
            String keyword,
            String userType,
            String status,
            Integer page,
            Integer size) {
        return mobileUsers(
                keyword,
                userType,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                page,
                size
        );
    }

    private CompiledQuery compileCustomQuery(
            String customFilters,
            String groupBy,
            String sorts,
            String sortField,
            String sortDirection
    ) {
        if (customFieldQueryService == null) return null;
        return customFieldQueryService.compile(
                "mobile-users",
                customFilters,
                groupBy,
                sorts,
                NativeOrderPolicy.of(
                        MOBILE_USER_ORDER_FIELDS,
                        sortField,
                        sortDirection,
                        "mu.updated_at",
                        "mu.id"
                )
        );
    }

    @PostMapping("/mobile-users/{id}/bind-tenant")
    public ApiResponse<Void> bindTenant(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        throw new BusinessException(410, "已停用任意租户ID绑定，请从验证手机号计算出的合法候选身份中选择");
    }

    @PostMapping("/mobile-users/{id}/unbind-tenant")
    public ApiResponse<Void> unbindTenant(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        ensureMobileUserExists(id);
        mobileIdentityService.unbindCurrent(id, principal.userId(), "ADMIN_UNBIND");
        return ApiResponse.success();
    }

    @GetMapping("/mobile-users/{id}")
    public ApiResponse<Map<String, Object>> mobileUserDetail(@PathVariable Long id) {
        ensureMobileUserExists(id);
        Map<String, Object> detail = new LinkedHashMap<>(jdbcTemplate.queryForMap(
                """
                SELECT id, code, nickname, real_name AS realName, phone,
                       phone_verified_at AS phoneVerifiedAt, user_type AS userType,
                       current_bind_id AS currentBindId, status,
                       last_login_at AS lastLoginAt, created_at AS createdAt, updated_at AS updatedAt
                FROM mobile_user
                WHERE id = ? AND deleted = 0
                """,
                id
        ));
        detail.put("verifiedPhone", maskPhone(detail.get("phone")));
        detail.remove("phone");
        detail.put("availableIdentities", mobileIdentityService.adminIdentityCandidates(id));
        detail.put("auditRecords", jdbcTemplate.queryForList(
                """
                SELECT id, action, previous_bind_id AS previousBindId, next_bind_id AS nextBindId,
                       operator_sys_user_id AS operatorSysUserId, detail, created_at AS createdAt
                FROM mobile_identity_audit
                WHERE mobile_user_id = ? AND deleted = 0
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """,
                id
        ));
        return ApiResponse.success(detail);
    }

    @GetMapping("/mobile-users/{id}/identity-candidates")
    public ApiResponse<List<Map<String, Object>>> identityCandidates(@PathVariable Long id) {
        ensureMobileUserExists(id);
        return ApiResponse.success(mobileIdentityService.adminIdentityCandidates(id));
    }

    @PostMapping("/mobile-users/{id}/default-identity")
    public ApiResponse<Void> setDefaultIdentity(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        ensureMobileUserExists(id);
        mobileIdentityService.adminSelectIdentity(
                id,
                JdbcMaps.requiredLong(body, "请选择合法候选身份", "bindId"),
                principal.userId()
        );
        return ApiResponse.success();
    }

    @PostMapping("/mobile-users/{id}/unbind")
    public ApiResponse<Void> unbind(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        ensureMobileUserExists(id);
        mobileIdentityService.unbindCurrent(id, principal.userId(), "ADMIN_UNBIND");
        return ApiResponse.success();
    }

    @PatchMapping("/mobile-users/{id}/status")
    public ApiResponse<Void> updateStatus(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        ensureMobileUserExists(id);
        String status = JdbcMaps.strOr(body, "ENABLED", "status").trim().toUpperCase();
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(400, "小程序用户状态无效");
        }
        mobileIdentityService.adminUpdateStatus(id, status, principal.userId());
        return ApiResponse.success();
    }

    private void appendFilters(
            StringBuilder where,
            List<Object> args,
            String keyword,
            String userType,
            String status,
            String code,
            String phone,
            String nickname,
            String realName,
            String tenantName,
            String tenantContactName,
            String boundSysRealName) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!normalizedKeyword.isEmpty()) {
            where.append("""
                    AND (
                      mu.phone LIKE ?
                      OR mu.nickname LIKE ?
                      OR mu.real_name LIKE ?
                      OR mu.code LIKE ?
                      OR t.name LIKE ?
                      OR tc.name LIKE ?
                    )
                    """);
            String like = "%" + normalizedKeyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (userType != null && !userType.trim().isEmpty()) {
            where.append(" AND mu.user_type = ?");
            args.add(userType.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND mu.status = ?");
            args.add(status.trim());
        }
        appendLikeFilter(where, args, "mu.code", code);
        appendLikeFilter(where, args, "mu.phone", phone);
        appendLikeFilter(where, args, "mu.nickname", nickname);
        appendLikeFilter(where, args, "mu.real_name", realName);
        appendLikeFilter(where, args, "t.name", tenantName);
        appendLikeFilter(where, args, "tc.name", tenantContactName);
        appendLikeFilter(where, args, "su.real_name", boundSysRealName);
    }

    private void appendLikeFilter(StringBuilder where, List<Object> args, String expression, String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return;
        }
        where.append(" AND ").append(expression).append(" LIKE ?");
        args.add("%" + normalized + "%");
    }

    private void ensureMobileUserExists(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM mobile_user WHERE id = ? AND deleted = 0 LIMIT 1",
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "小程序用户不存在");
        }
    }

    private static String normalizedPhoneSql(String column) {
        String digits = "REGEXP_REPLACE(COALESCE(" + column + ", ''), '[^0-9]', '')";
        return """
                CASE
                  WHEN %1$s REGEXP '^[0-9]{11}$' THEN %1$s
                  WHEN %1$s REGEXP '^86[0-9]{11}$' THEN RIGHT(%1$s, 11)
                  WHEN %1$s REGEXP '^0086[0-9]{11}$' THEN RIGHT(%1$s, 11)
                  ELSE NULL
                END
                """.formatted(digits).trim();
    }

    private String maskPhone(Object phoneValue) {
        String phone = phoneValue == null ? "" : String.valueOf(phoneValue).trim();
        if (phone.length() < 7) return phone.isBlank() ? null : "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public ApiResponse<Void> unbindTenant(Long id) {
        ensureMobileUserExists(id);
        mobileIdentityService.unbindCurrent(id, 0L, "ADMIN_UNBIND");
        return ApiResponse.success();
    }
}

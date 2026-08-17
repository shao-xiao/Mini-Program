package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.mobile.service.MobileCapabilityMapper;
import com.dehui.property.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class MobileApiSupport {

    private static final List<String> PUBLIC_CAPABILITIES = List.of(
            "announcement.view",
            "investment.view",
            "investment.booking.create",
            "meeting.public.view"
    );

    private static final List<String> TENANT_RESPONSIBLE_CAPABILITIES = List.of(
            "announcement.view",
            "investment.view",
            "investment.booking.create",
            "meeting.public.view",
            "my-business.view",
            "bill.view",
            "contract.view",
            "workorder.create",
            "workorder.view",
            "meeting.book",
            "meeting.company.view",
            "visitor.create",
            "visitor.company.view",
            "energy.company.view",
            "parking.company.view",
            "parking.mine.view"
    );

    private static final List<String> TENANT_EMPLOYEE_CAPABILITIES = List.of(
            "announcement.view",
            "investment.view",
            "investment.booking.create",
            "meeting.public.view",
            "my-business.view",
            "workorder.create",
            "workorder.view",
            "meeting.book",
            "meeting.mine.view",
            "visitor.create",
            "visitor.mine.view",
            "parking.mine.view"
    );

    private MobileApiSupport() {
    }

    public static Optional<AuthPrincipal> currentPrincipal(JdbcTemplate jdbcTemplate, Long mobileUserId) {
        if (jdbcTemplate == null || mobileUserId == null) return Optional.empty();
        Map<String, Object> row = identityRow(jdbcTemplate, mobileUserId);
        if (row == null) return Optional.empty();

        String userType = effectiveUserType(jdbcTemplate, row);
        Long tenantId = "TENANT".equals(userType) ? longValue(row.get("tenantId")) : null;
        List<String> roles = switch (userType) {
            case "TENANT" -> List.of("MOBILE_USER", "MOBILE_TENANT");
            case "STAFF" -> List.of("MOBILE_USER", "MOBILE_STAFF");
            default -> List.of("MOBILE_USER");
        };
        return Optional.of(new AuthPrincipal(
                mobileUserId,
                "mobile:" + mobileUserId,
                userType,
                tenantId,
                roles,
                capabilities(jdbcTemplate, row, userType)
        ));
    }

    public static List<String> capabilities(String userType) {
        return switch (normalizeUserType(userType)) {
            case "TENANT" -> TENANT_EMPLOYEE_CAPABILITIES;
            case "STAFF" -> MobileCapabilityMapper.STAFF_BASE_CAPABILITIES;
            default -> PUBLIC_CAPABILITIES;
        };
    }

    public static boolean isTenantManager(JdbcTemplate jdbcTemplate, AuthPrincipal principal) {
        return principal != null
                && "TENANT".equals(principal.userType())
                && isTenantManager(identityRow(jdbcTemplate, principal.userId()));
    }

    public static Long tenantContactId(JdbcTemplate jdbcTemplate, AuthPrincipal principal) {
        if (principal == null || !"TENANT".equals(principal.userType())) return null;
        Map<String, Object> row = identityRow(jdbcTemplate, principal.userId());
        return row == null ? null : longValue(row.get("tenantContactId"));
    }

    public static void requireCapability(AuthPrincipal principal, String capability) {
        if (principal == null
                || principal.permissions() == null
                || !principal.permissions().contains(capability)) {
            throw BusinessException.forbidden("当前移动身份没有该功能权限");
        }
    }

    /**
     * Compatibility profile embedded by business endpoints. It intentionally
     * contains presentation fields only and never leaks relationship IDs.
     */
    public static Map<String, Object> profile(JdbcTemplate jdbcTemplate, AuthPrincipal principal) {
        Map<String, Object> me = me(jdbcTemplate, principal, List.of(), null);
        LinkedHashMap<String, Object> profile = new LinkedHashMap<>(map(me.get("profile")));
        profile.put("userType", me.get("userType"));
        profile.put("role", me.get("role"));
        profile.put("bindingState", me.get("bindingState"));
        profile.put("contactRole", me.get("contactRole"));
        profile.put("tenantInfo", me.get("tenantInfo"));
        profile.put("staffInfo", me.get("staffInfo"));
        profile.put("capabilities", me.get("capabilities"));
        profile.put("dataScope", me.get("dataScope"));
        return profile;
    }

    public static Map<String, Object> me(JdbcTemplate jdbcTemplate, AuthPrincipal principal) {
        return me(jdbcTemplate, principal, List.of(), null);
    }

    public static Map<String, Object> me(
            JdbcTemplate jdbcTemplate,
            AuthPrincipal principal,
            List<Map<String, Object>> availableIdentities,
            String explicitBindingState) {
        String userType = normalizeUserType(principal == null ? null : principal.userType());
        Map<String, Object> row = principal == null ? null : identityRow(jdbcTemplate, principal.userId());
        if (principal != null && row == null) {
            userType = "PUBLIC";
        }
        String contactRole = "TENANT".equals(userType) ? contactRole(row) : null;
        String dataScope = switch (userType) {
            case "TENANT" -> "RESPONSIBLE".equals(contactRole) ? "TENANT_COMPANY" : "SELF";
            case "STAFF" -> "STAFF_RBAC";
            default -> "PUBLIC";
        };
        String bindingState = explicitBindingState;
        if (bindingState == null || bindingState.isBlank()) {
            if ("TENANT".equals(userType) || "STAFF".equals(userType)) {
                bindingState = "BOUND";
            } else if (row == null || stringValue(row.get("phone")).isBlank()) {
                bindingState = "UNVERIFIED";
            } else {
                bindingState = "UNMATCHED";
            }
        }

        String name = row == null ? "" : stringValue(row.get("nickname"));
        if (row != null && "TENANT".equals(userType) && !stringValue(row.get("contactName")).isBlank()) {
            name = stringValue(row.get("contactName"));
        } else if (row != null && "STAFF".equals(userType) && !stringValue(row.get("boundSysRealName")).isBlank()) {
            name = stringValue(row.get("boundSysRealName"));
        }

        LinkedHashMap<String, Object> displayProfile = new LinkedHashMap<>();
        displayProfile.put("name", name);
        displayProfile.put("maskedPhone", maskPhone(row == null ? "" : stringValue(row.get("phone"))));
        displayProfile.put("avatarUrl", row == null ? "" : stringValue(row.get("avatarUrl")));

        Map<String, Object> tenantInfo = null;
        if ("TENANT".equals(userType)) {
            tenantInfo = new LinkedHashMap<>();
            tenantInfo.put("name", row == null ? "" : stringValue(row.get("boundTenantName")));
            tenantInfo.put("contactRoleLabel", "RESPONSIBLE".equals(contactRole) ? "租户负责人" : "租户员工");
            tenantInfo.put("logoFileName", row == null ? "" : stringValue(row.get("tenantLogoFileName")));
            tenantInfo.put(
                    "logoUrl",
                    row == null || longValue(row.get("tenantLogoFileId")) == null
                            ? ""
                            : "/api/mobile/files/" + longValue(row.get("tenantLogoFileId"))
            );
        }
        Map<String, Object> staffInfo = null;
        if ("STAFF".equals(userType)) {
            staffInfo = new LinkedHashMap<>();
            staffInfo.put("name", row == null ? "" : stringValue(row.get("boundSysRealName")));
            staffInfo.put("mobileWorkbenchEnabled", row != null
                    && truthy(row.get("mobileEnabled"))
                    && truthy(row.get("hasMobileWorkbenchAccess")));
        }

        List<String> capabilities = principal == null || row == null || principal.permissions() == null
                ? List.of()
                : principal.permissions();
        LinkedHashMap<String, Object> me = new LinkedHashMap<>();
        me.put("sessionState", principal == null ? "ANONYMOUS" : "AUTHENTICATED");
        me.put("userType", userType);
        me.put("bindingState", bindingState);
        me.put("contactRole", contactRole);
        me.put("profile", displayProfile);
        me.put("tenantInfo", tenantInfo);
        me.put("staffInfo", staffInfo);
        me.put("capabilities", capabilities);
        me.put("dataScope", Map.of("type", dataScope));
        me.put("availableIdentities", availableIdentities == null ? List.of() : availableIdentities);
        me.put("role", role(userType));
        me.put("tenantBound", "TENANT".equals(userType));
        me.put("staffBound", "STAFF".equals(userType));
        return me;
    }

    public static Long tenantId(AuthPrincipal principal) {
        return principal != null && "TENANT".equals(principal.userType()) ? principal.tenantId() : null;
    }

    public static Long requireTenantId(AuthPrincipal principal) {
        Long tenantId = tenantId(principal);
        if (tenantId == null) {
            throw BusinessException.forbidden("当前功能仅限已绑定租户身份使用");
        }
        return tenantId;
    }

    public static String mobileUserPhone(JdbcTemplate jdbcTemplate, AuthPrincipal principal) {
        if (principal == null) return null;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT phone FROM mobile_user WHERE id = ? AND status IN ('ACTIVE', 'ENABLED') AND deleted = 0 LIMIT 1",
                principal.userId()
        );
        return rows.isEmpty() ? null : stringValue(rows.getFirst().get("phone"));
    }

    public static Long requireStaffUserId(JdbcTemplate jdbcTemplate, AuthPrincipal principal) {
        if (principal == null || !"STAFF".equals(normalizeUserType(principal.userType()))) {
            throw BusinessException.forbidden("当前功能仅限已授权物业员工使用");
        }
        Map<String, Object> row = identityRow(jdbcTemplate, principal.userId());
        if (row == null
                || !"STAFF".equals(effectiveUserType(jdbcTemplate, row))
                || longValue(row.get("boundSysUserId")) == null) {
            throw BusinessException.forbidden("物业员工移动绑定已失效");
        }
        return longValue(row.get("boundSysUserId"));
    }

    private static List<String> capabilities(
            JdbcTemplate jdbcTemplate,
            Map<String, Object> identity,
            String userType) {
        if ("TENANT".equals(userType)) {
            return isTenantManager(identity)
                    ? TENANT_RESPONSIBLE_CAPABILITIES
                    : TENANT_EMPLOYEE_CAPABILITIES;
        }
        if (!"STAFF".equals(userType)) {
            if ("PUBLIC".equals(userType)
                    && identity.get("phoneVerifiedAt") != null
                    && !stringValue(identity.get("phone")).isBlank()) {
                ArrayList<String> capabilities = new ArrayList<>(PUBLIC_CAPABILITIES);
                capabilities.add("meeting.guest.book");
                return List.copyOf(capabilities);
            }
            return PUBLIC_CAPABILITIES;
        }
        return MobileCapabilityMapper.fromPermissionCodes(
                staffPermissionCodes(jdbcTemplate, longValue(identity.get("boundSysUserId")))
        );
    }

    private static List<String> staffPermissionCodes(JdbcTemplate jdbcTemplate, Long sysUserId) {
        if (sysUserId == null) return List.of();
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT permission_record.code
                FROM sys_user_role user_role
                INNER JOIN sys_role role_record
                  ON role_record.id = user_role.role_id
                 AND role_record.deleted = 0
                 AND role_record.status IN ('ACTIVE', 'ENABLED')
                INNER JOIN sys_role_permission role_permission
                  ON role_permission.role_id = role_record.id
                 AND role_permission.deleted = 0
                 AND role_permission.status IN ('ACTIVE', 'ENABLED')
                INNER JOIN sys_permission permission_record
                  ON permission_record.id = role_permission.permission_id
                 AND permission_record.deleted = 0
                 AND permission_record.status IN ('ACTIVE', 'ENABLED')
                WHERE user_role.user_id = ?
                  AND user_role.deleted = 0
                  AND user_role.status IN ('ACTIVE', 'ENABLED')
                """,
                String.class,
                sysUserId
        );
    }

    private static Map<String, Object> identityRow(JdbcTemplate jdbcTemplate, Long mobileUserId) {
        if (jdbcTemplate == null || mobileUserId == null) return null;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT mobile.id AS mobileUserId,
                       mobile.nickname,
                       mobile.avatar_url AS avatarUrl,
                       mobile.phone,
                       mobile.phone_verified_at AS phoneVerifiedAt,
                       mobile.phone_verification_source AS phoneVerificationSource,
                       mobile.user_type AS declaredUserType,
                       mobile.current_bind_id AS currentBindId,
                       bind_record.bind_type AS currentBindType,
                       bind_record.tenant_id AS tenantId,
                       bind_record.tenant_contact_id AS tenantContactId,
                       contact.role AS contactRole,
                       contact.is_primary AS isPrimaryContact,
                       tenant_record.name AS boundTenantName,
                       tenant_record.status AS tenantStatus,
                       tenant_logo.id AS tenantLogoFileId,
                        tenant_logo.original_name AS tenantLogoFileName,
                        contact.name AS contactName,
                        contact.phone AS contactPhone,
                        contact.status AS contactStatus,
                        bind_record.sys_user_id AS boundSysUserId,
                        staff.username AS boundSysUsername,
                        staff.real_name AS boundSysRealName,
                        staff.phone AS staffPhone,
                        staff.status AS staffStatus,
                        staff.mobile_enabled AS mobileEnabled,
                       EXISTS (
                         SELECT 1
                         FROM sys_user_role user_role
                         INNER JOIN sys_role role_record
                           ON role_record.id = user_role.role_id
                          AND role_record.deleted = 0
                          AND role_record.status IN ('ACTIVE', 'ENABLED')
                         INNER JOIN sys_role_permission role_permission
                           ON role_permission.role_id = role_record.id
                          AND role_permission.deleted = 0
                          AND role_permission.status IN ('ACTIVE', 'ENABLED')
                         INNER JOIN sys_permission permission_record
                           ON permission_record.id = role_permission.permission_id
                          AND permission_record.deleted = 0
                          AND permission_record.status IN ('ACTIVE', 'ENABLED')
                         WHERE user_role.user_id = staff.id
                           AND user_role.deleted = 0
                           AND user_role.status IN ('ACTIVE', 'ENABLED')
                           AND permission_record.code IN ('staff-mobile:access', 'staff-mobile:*', '*')
                       ) AS hasMobileWorkbenchAccess
                FROM mobile_user mobile
                LEFT JOIN mobile_user_bind bind_record
                  ON bind_record.id = mobile.current_bind_id
                 AND bind_record.mobile_user_id = mobile.id
                 AND bind_record.status = 'ENABLED'
                 AND bind_record.deleted = 0
                LEFT JOIN tenant tenant_record
                  ON tenant_record.id = bind_record.tenant_id
                 AND tenant_record.deleted = 0
                LEFT JOIN file_upload tenant_logo
                  ON tenant_logo.id = tenant_record.logo_file_id
                 AND tenant_logo.biz_type = 'TENANT_LOGO'
                 AND tenant_logo.biz_id = tenant_record.id
                 AND tenant_logo.workspace_key = 'tenants'
                 AND tenant_logo.access_scope = 'INTERNAL'
                 AND tenant_logo.status = 'ENABLED'
                 AND tenant_logo.deleted = 0
                LEFT JOIN tenant_contact contact
                  ON contact.id = bind_record.tenant_contact_id
                 AND contact.deleted = 0
                LEFT JOIN sys_user staff
                  ON staff.id = bind_record.sys_user_id
                 AND staff.deleted = 0
                WHERE mobile.id = ?
                  AND mobile.status IN ('ACTIVE', 'ENABLED')
                  AND mobile.deleted = 0
                LIMIT 1
                """,
                mobileUserId
        );
        if (rows.isEmpty() || longValue(rows.getFirst().get("mobileUserId")) == null) return null;
        return rows.getFirst();
    }

    private static String effectiveUserType(JdbcTemplate jdbcTemplate, Map<String, Object> row) {
        String declared = normalizeUserType(stringValue(row.get("declaredUserType")));
        String bindType = stringValue(row.get("currentBindType"));
        if ("TENANT".equals(declared)
                && "TENANT".equals(bindType)
                && longValue(row.get("tenantId")) != null
                 && longValue(row.get("tenantContactId")) != null
                 && isActive(row.get("tenantStatus"))
                 && isActive(row.get("contactStatus"))
                 && hasVerifiedMatchingPhone(row, "contactPhone")) {
            return "TENANT";
        }
        if ("STAFF".equals(declared)
                && "INTERNAL".equals(bindType)
                && longValue(row.get("boundSysUserId")) != null
                && isActive(row.get("staffStatus"))
                && truthy(row.get("mobileEnabled"))
                && truthy(row.get("hasMobileWorkbenchAccess"))
                && "WECHAT_API".equalsIgnoreCase(stringValue(row.get("phoneVerificationSource")))
                && hasVerifiedMatchingPhone(row, "staffPhone")) {
            return "STAFF";
        }
        return "PUBLIC";
    }

    private static boolean hasVerifiedMatchingPhone(Map<String, Object> row, String businessPhoneKey) {
        if (row == null || row.get("phoneVerifiedAt") == null) return false;
        String verifiedPhone = normalizedMobilePhone(row.get("phone"));
        String businessPhone = normalizedMobilePhone(row.get(businessPhoneKey));
        return !verifiedPhone.isBlank() && verifiedPhone.equals(businessPhone);
    }

    private static String normalizedMobilePhone(Object value) {
        String digits = stringValue(value).replaceAll("[^0-9]", "");
        if (digits.startsWith("0086") && digits.length() == 15) {
            digits = digits.substring(4);
        } else if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }
        return digits.matches("\\d{11}") ? digits : "";
    }

    public static String normalizeUserType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if ("TENANT".equals(normalized)) return "TENANT";
        if ("INTERNAL".equals(normalized) || "STAFF".equals(normalized)) return "STAFF";
        return "PUBLIC";
    }

    private static String contactRole(Map<String, Object> identity) {
        return isTenantManager(identity) ? "RESPONSIBLE" : "EMPLOYEE";
    }

    private static boolean isTenantManager(Map<String, Object> identity) {
        if (identity == null) return false;
        if (truthy(identity.get("isPrimaryContact"))) return true;
        String role = stringValue(identity.get("contactRole")).trim().toUpperCase();
        return List.of("OWNER", "MANAGER", "ADMIN", "PRIMARY", "RESPONSIBLE").contains(role);
    }

    private static boolean isActive(Object value) {
        String status = stringValue(value).toUpperCase();
        return "ACTIVE".equals(status) || "ENABLED".equals(status);
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = stringValue(value);
        return "1".equals(text) || "TRUE".equalsIgnoreCase(text) || "YES".equalsIgnoreCase(text);
    }

    private static String role(String userType) {
        return switch (userType) {
            case "TENANT" -> "tenant";
            case "STAFF" -> "staff";
            default -> "guest";
        };
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }
}

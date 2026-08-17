package com.dehui.property.modules.tenant.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.ContactValidators;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.modules.shared.infrastructure.DomainEventRepository;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TenantController {

    private static final String DEFAULT_CONTACT_PASSWORD = "123456";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private DomainEventRepository domainEventRepository;

    @GetMapping({"/tenants", "/tenant/list"})
    public ApiResponse<List<Map<String, Object>>> tenants() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id,
                       code AS tenantCode,
                       name AS tenantName,
                       name,
                       contact_person AS contactPerson,
                       contact_phone AS contactPhone,
                       contact_email AS contactEmail,
                       credit_code AS businessLicense,
                       credit_code AS creditCode,
                       short_name AS companyShortName,
                       invoice_title AS invoiceTitle,
                       tax_number AS taxNumber,
                       service_manager_user_id AS serviceManagerUserId,
                       status,
                       created_at AS createdAt,
                       updated_at AS updatedAt,
                       remark
                FROM tenant
                WHERE deleted = 0
                ORDER BY updated_at DESC, id DESC
                """
        ));
    }

    @PostMapping("/tenant/save")
    public ApiResponse<Void> saveTenantEndpoint(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return saveTenant(body, principal.userId());
    }

    public ApiResponse<Void> saveTenant(Map<String, Object> body) {
        return saveTenant(body, 0L);
    }

    private ApiResponse<Void> saveTenant(Map<String, Object> body, Long operatorId) {
        String tenantName = JdbcMaps.requiredStr(body, "Tenant name is required", "tenantName", "name");
        Long id = JdbcMaps.longVal(body, "id");
        String status = JdbcMaps.strOr(body, "ACTIVE", "status");
        String contactPhone = ContactValidators.optionalPhone(JdbcMaps.str(body, "contactPhone", "phone"));
        String contactEmail = ContactValidators.optionalEmail(JdbcMaps.str(body, "contactEmail", "email"));
        String contactPerson = JdbcMaps.str(body, "contactPerson");
        String creditCode = JdbcMaps.str(body, "businessLicense", "creditCode", "unifiedCreditCode");
        String invoiceTitle = JdbcMaps.str(body, "invoiceTitle");
        String taxNumber = JdbcMaps.str(body, "taxNumber");
        Long serviceManagerUserId = JdbcMaps.longVal(body, "serviceManagerUserId");
        validateServiceManager(serviceManagerUserId);
        if (id != null && !active(status)) {
            ensureTenantCanBeDeactivated(id);
        }
        try {
            if (id == null) {
                String tenantCode = JdbcMaps.code("TEN");
                jdbcTemplate.update(
                        """
                        INSERT INTO tenant (
                            code, name, short_name, contact_person, credit_code, invoice_title,
                            tax_number, service_manager_user_id, contact_phone, contact_email,
                            status, created_by, updated_by, remark
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        tenantCode,
                        tenantName,
                        JdbcMaps.str(body, "companyShortName", "shortName"),
                        contactPerson,
                        creditCode,
                        blankDefault(invoiceTitle, tenantName),
                        blankDefault(taxNumber, creditCode),
                        serviceManagerUserId,
                        contactPhone,
                        contactEmail,
                        status,
                        operatorId,
                        operatorId,
                        JdbcMaps.str(body, "remark")
                );
                id = jdbcTemplate.queryForObject(
                        "SELECT id FROM tenant WHERE code = ? AND deleted = 0 LIMIT 1",
                        Long.class,
                        tenantCode
                );
            } else {
                Long previousManagerId = jdbcTemplate.queryForObject(
                        "SELECT service_manager_user_id FROM tenant WHERE id = ? AND deleted = 0",
                        Long.class,
                        id
                );
                int updated = jdbcTemplate.update(
                        """
                        UPDATE tenant
                        SET name = ?, short_name = ?, contact_person = ?, credit_code = ?,
                            invoice_title = ?, tax_number = ?, service_manager_user_id = ?,
                            contact_phone = ?, contact_email = ?, status = ?, updated_by = ?, remark = ?
                        WHERE id = ? AND deleted = 0
                        """,
                        tenantName,
                        JdbcMaps.str(body, "companyShortName", "shortName"),
                        contactPerson,
                        creditCode,
                        blankDefault(invoiceTitle, tenantName),
                        blankDefault(taxNumber, creditCode),
                        serviceManagerUserId,
                        contactPhone,
                        contactEmail,
                        status,
                        operatorId,
                        JdbcMaps.str(body, "remark"),
                        id
                );
                ensureUpdated(updated, "Tenant not found");
                if (!java.util.Objects.equals(previousManagerId, serviceManagerUserId)) {
                    auditTenantRelation(id, "SERVICE_MANAGER_CHANGED", previousManagerId, serviceManagerUserId, operatorId);
                }
            }
            auditTenantRelation(id, "TENANT_SAVED", null, serviceManagerUserId, operatorId);
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "Tenant already exists");
        }
    }

    @Transactional
    @DeleteMapping({"/tenants/{id}", "/tenant/{id}"})
    public ApiResponse<Void> deleteTenant(@PathVariable Long id) {
        ensureTenantCanBeDeleted(id);
        int updated = jdbcTemplate.update(
                """
                UPDATE tenant
                SET deleted = 1, updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                0L,
                id
        );
        ensureUpdated(updated, "Tenant not found");
        return ApiResponse.success();
    }

    @GetMapping("/tenant/{tenantId}/contacts")
    public ApiResponse<List<Map<String, Object>>> tenantContacts(@PathVariable Long tenantId) {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT contact_record.id,
                       contact_record.tenant_id AS tenantId,
                       contact_record.name,
                       contact_record.phone,
                       contact_record.email,
                       contact_record.position,
                       contact_record.role,
                       contact_record.is_primary AS isPrimary,
                       contact_record.is_emergency AS isEmergency,
                       contact_record.status,
                       contact_record.remark,
                       CASE WHEN COALESCE(binding_summary.boundCount, 0) > 0 THEN 'BOUND' ELSE 'UNBOUND' END AS bindingStatus,
                       COALESCE(binding_summary.boundCount, 0) AS boundMobileUserCount,
                       binding_summary.lastLoginAt
                FROM tenant_contact contact_record
                LEFT JOIN (
                    SELECT bind_record.tenant_contact_id,
                           COUNT(DISTINCT bind_record.mobile_user_id) AS boundCount,
                           MAX(mobile_record.last_login_at) AS lastLoginAt
                    FROM mobile_user_bind bind_record
                    INNER JOIN mobile_user mobile_record
                      ON mobile_record.id = bind_record.mobile_user_id
                     AND mobile_record.deleted = 0
                    WHERE bind_record.bind_type = 'TENANT'
                      AND bind_record.status = 'ENABLED'
                      AND bind_record.deleted = 0
                    GROUP BY bind_record.tenant_contact_id
                ) binding_summary
                  ON binding_summary.tenant_contact_id = contact_record.id
                WHERE contact_record.tenant_id = ? AND contact_record.deleted = 0
                ORDER BY contact_record.is_primary DESC,
                         contact_record.is_emergency DESC,
                         contact_record.id DESC
                """,
                tenantId
        ));
    }

    @PostMapping("/tenant/{tenantId}/contacts")
    @Transactional
    public ApiResponse<Map<String, Object>> saveTenantContactEndpoint(
            @PathVariable Long tenantId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return saveTenantContact(tenantId, body, principal.userId());
    }

    public ApiResponse<Map<String, Object>> saveTenantContact(Long tenantId, Map<String, Object> body) {
        return saveTenantContact(tenantId, body, 0L);
    }

    private ApiResponse<Map<String, Object>> saveTenantContact(
            Long tenantId,
            Map<String, Object> body,
            Long operatorId
    ) {
        String name = JdbcMaps.requiredStr(body, "Contact name is required", "name");
        String phone = ContactValidators.requiredPhone(JdbcMaps.str(body, "phone", "contactPhone"));
        String email = ContactValidators.optionalEmail(JdbcMaps.str(body, "email", "contactEmail"));
        String position = JdbcMaps.str(body, "position");
        String contactRole = normalizedContactRole(body);
        String nextStatus = JdbcMaps.strOr(body, "ACTIVE", "status");
        boolean primary = Boolean.TRUE.equals(JdbcMaps.bool(body, false, "isPrimary"));
        if (primary) {
            contactRole = "RESPONSIBLE";
        }
        if ("RESPONSIBLE".equals(contactRole) && !active(nextStatus)) {
            throw new BusinessException(409, "租户负责人必须保持启用；请先设置新的负责人");
        }
        boolean emergency = Boolean.TRUE.equals(JdbcMaps.bool(body, false, "isEmergency"));
        if (emergency && !active(nextStatus)) {
            throw new BusinessException(409, "紧急联系人必须保持启用");
        }
        Long id = JdbcMaps.longVal(body, "id");
        Integer duplicatePhone = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tenant_contact
                WHERE tenant_id = ? AND phone = ? AND deleted = 0
                  AND (? IS NULL OR id <> ?)
                """,
                Integer.class,
                tenantId,
                phone,
                id,
                id
        );
        if (duplicatePhone != null && duplicatePhone > 0) {
            throw new BusinessException(400, "同一租户下该手机号已存在");
        }

        String previousPhone = null;
        if (id != null) {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    """
                    SELECT phone, role, is_primary AS isPrimary, is_emergency AS isEmergency, status
                    FROM tenant_contact
                    WHERE id = ? AND tenant_id = ? AND deleted = 0
                    FOR UPDATE
                    """,
                    id,
                    tenantId
            );
            if (existing.isEmpty()) throw new BusinessException(404, "Contact not found");
            previousPhone = String.valueOf(existing.getFirst().get("phone"));
            if (!active(nextStatus)) {
                ensureContactCanBeDisabled(existing.getFirst());
            }
        }

        if (primary) {
            jdbcTemplate.update(
                    """
                    UPDATE tenant_contact
                    SET is_primary = 0,
                        updated_by = ?
                    WHERE tenant_id = ? AND deleted = 0 AND (? IS NULL OR id <> ?)
                    """,
                    operatorId,
                    tenantId,
                    id,
                    id
            );
        }

        if (id == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO tenant_contact
                      (code, tenant_id, name, phone, email, position, password_hash,
                       role, is_primary, status, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    JdbcMaps.code("TC"),
                    tenantId,
                    name,
                    phone,
                    email,
                    position,
                    passwordEncoder.encode(java.util.UUID.randomUUID().toString()),
                    contactRole,
                    primary ? 1 : 0,
                    nextStatus,
                    operatorId,
                    operatorId
            );
            id = jdbcTemplate.queryForObject(
                    "SELECT id FROM tenant_contact WHERE tenant_id = ? AND phone = ? AND deleted = 0 LIMIT 1",
                    Long.class,
                    tenantId,
                    phone
            );
        } else {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE tenant_contact
                    SET name = ?, phone = ?, email = ?, position = ?, role = ?, is_primary = ?, status = ?, updated_by = ?
                    WHERE id = ? AND tenant_id = ? AND deleted = 0
                    """,
                    name,
                    phone,
                    email,
                    position,
                    contactRole,
                    primary ? 1 : 0,
                    nextStatus,
                    operatorId,
                    id,
                    tenantId
            );
            ensureUpdated(updated, "Contact not found");
            if (!phone.equals(previousPhone) || !List.of("ACTIVE", "ENABLED").contains(nextStatus)) {
                invalidateTenantContactBindings(
                        id,
                        phone.equals(previousPhone) ? "TENANT_CONTACT_DISABLED" : "TENANT_CONTACT_PHONE_CHANGED",
                        operatorId
                );
            }
        }
        setEmergencyContactInternal(tenantId, id, emergency, operatorId);
        auditContact(id, "TENANT_CONTACT_SAVED", tenantId, operatorId);
        long boundCount = count(
                """
                SELECT COUNT(DISTINCT mobile_user_id)
                FROM mobile_user_bind
                WHERE tenant_contact_id = ?
                  AND bind_type = 'TENANT'
                  AND status = 'ENABLED'
                  AND deleted = 0
                """,
                id
        );
        long identityCount = count(
                """
                SELECT (
                    SELECT COUNT(*) FROM tenant_contact
                    WHERE phone = ? AND status IN ('ACTIVE', 'ENABLED') AND deleted = 0
                ) + (
                    SELECT COUNT(*) FROM sys_user
                    WHERE phone = ? AND status IN ('ACTIVE', 'ENABLED')
                      AND mobile_enabled = 1 AND deleted = 0
                )
                """,
                phone,
                phone
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("contactId", id);
        response.put("contactRole", contactRole);
        response.put("bindingStatus", boundCount > 0 ? "BOUND" : "UNBOUND");
        response.put("boundMobileUserCount", boundCount);
        response.put("multipleIdentities", identityCount > 1);
        response.put("loginHint", "联系人已生效，该手机号可直接使用微信手机号快捷登录，无需账号密码。");
        return ApiResponse.success(response);
    }

    @PostMapping("/tenant/contacts/{id}/deactivate")
    @Transactional
    public ApiResponse<Void> deactivateContactEndpoint(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return deactivateContact(id, principal.userId());
    }

    public ApiResponse<Void> deactivateContact(Long id) {
        return deactivateContact(id, 0L);
    }

    private ApiResponse<Void> deactivateContact(Long id, Long operatorId) {
        Map<String, Object> existing = contactForUpdate(id, null);
        ensureContactCanBeDisabled(existing);
        int updated = jdbcTemplate.update(
                "UPDATE tenant_contact SET status = 'INACTIVE', is_emergency = 0, updated_by = ? WHERE id = ? AND deleted = 0",
                operatorId,
                id
        );
        ensureUpdated(updated, "Contact not found");
        invalidateTenantContactBindings(id, "TENANT_CONTACT_DISABLED", operatorId);
        auditContact(id, "TENANT_CONTACT_DISABLED", number(existing.get("tenantId")), operatorId);
        return ApiResponse.success();
    }

    @DeleteMapping("/tenant/{tenantId}/contacts/{id}")
    @Transactional
    public ApiResponse<Void> deleteTenantContactEndpoint(
            @PathVariable Long tenantId,
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return deleteTenantContact(tenantId, id, principal.userId());
    }

    public ApiResponse<Void> deleteTenantContact(Long tenantId, Long id) {
        return deleteTenantContact(tenantId, id, 0L);
    }

    private ApiResponse<Void> deleteTenantContact(Long tenantId, Long id, Long operatorId) {
        Map<String, Object> existing = contactForUpdate(id, tenantId);
        ensureContactCanBeDisabled(existing);
        invalidateTenantContactBindings(id, "TENANT_CONTACT_DELETED", operatorId);
        int updated = jdbcTemplate.update(
                """
                UPDATE tenant_contact
                SET deleted = 1, is_emergency = 0, updated_by = ?
                WHERE id = ? AND tenant_id = ? AND deleted = 0
                """,
                operatorId,
                id,
                tenantId
        );
        ensureUpdated(updated, "Contact not found");
        auditContact(id, "TENANT_CONTACT_DELETED", tenantId, operatorId);
        return ApiResponse.success();
    }

    @PostMapping("/tenant/contacts/{id}/responsible")
    @Transactional
    public ApiResponse<Void> setResponsibleContact(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        Map<String, Object> existing = contactForUpdate(id, null);
        if (!active(String.valueOf(existing.get("status")))) {
            throw new BusinessException(409, "停用联系人不能设为租户负责人");
        }
        Long tenantId = number(existing.get("tenantId"));
        jdbcTemplate.update(
                """
                UPDATE tenant_contact
                SET is_primary = 0,
                    updated_by = ?
                WHERE tenant_id = ? AND deleted = 0 AND id <> ?
                """,
                principal.userId(),
                tenantId,
                id
        );
        jdbcTemplate.update(
                """
                UPDATE tenant_contact
                SET role = 'RESPONSIBLE', is_primary = 1, updated_by = ?
                WHERE id = ? AND tenant_id = ? AND deleted = 0
                """,
                principal.userId(),
                id,
                tenantId
        );
        auditContact(id, "TENANT_RESPONSIBLE_CHANGED", tenantId, principal.userId());
        return ApiResponse.success();
    }

    @PostMapping("/tenant/contacts/{id}/emergency")
    @Transactional
    public ApiResponse<Void> setEmergencyContact(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        Map<String, Object> existing = contactForUpdate(id, null);
        if (!active(String.valueOf(existing.get("status")))) {
            throw new BusinessException(409, "停用联系人不能设为紧急联系人");
        }
        Long tenantId = number(existing.get("tenantId"));
        setEmergencyContactInternal(tenantId, id, true, principal.userId());
        return ApiResponse.success();
    }

    @PostMapping("/tenant/contacts/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetContactPassword(@PathVariable Long id) {
        throw new BusinessException(410, "租户联系人账号密码登录已停用，请使用微信手机号快捷登录");
    }

    private String normalizedContactRole(Map<String, Object> body) {
        String role = JdbcMaps.strOr(body, "EMPLOYEE", "contactRole", "role")
                .trim()
                .toUpperCase();
        return "RESPONSIBLE".equals(role) ? "RESPONSIBLE" : "EMPLOYEE";
    }

    private void invalidateTenantContactBindings(Long contactId, String action, Long operatorId) {
        List<Map<String, Object>> bindings = jdbcTemplate.queryForList(
                """
                SELECT bind_record.id AS bindId, bind_record.mobile_user_id AS mobileUserId,
                       mobile.current_bind_id AS currentBindId
                FROM mobile_user_bind bind_record
                INNER JOIN mobile_user mobile
                  ON mobile.id = bind_record.mobile_user_id AND mobile.deleted = 0
                WHERE bind_record.tenant_contact_id = ?
                  AND bind_record.bind_type = 'TENANT'
                  AND bind_record.deleted = 0
                """,
                contactId
        );
        jdbcTemplate.update(
                """
                UPDATE mobile_user_bind
                SET status = 'DISABLED', updated_by = ?
                WHERE tenant_contact_id = ? AND bind_type = 'TENANT' AND deleted = 0
                """,
                operatorId,
                contactId
        );
        for (Map<String, Object> binding : bindings) {
            Long bindId = ((Number) binding.get("bindId")).longValue();
            Long mobileUserId = ((Number) binding.get("mobileUserId")).longValue();
            Long currentBindId = binding.get("currentBindId") instanceof Number number ? number.longValue() : null;
            if (bindId.equals(currentBindId)) {
                jdbcTemplate.update(
                        """
                        UPDATE mobile_user
                        SET current_bind_id = NULL, user_type = 'PUBLIC',
                            phone_verified_at = NULL, updated_by = ?
                        WHERE id = ? AND deleted = 0
                        """,
                        operatorId,
                        mobileUserId
                );
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO mobile_identity_audit
                      (mobile_user_id, action, previous_bind_id, operator_sys_user_id, detail)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    mobileUserId,
                    action,
                    bindId,
                    operatorId,
                    "租户联系人变更导致身份需要重新验证"
            );
        }
    }

    @GetMapping("/tenant/{tenantId}/overview")
    public ApiResponse<Map<String, Object>> tenantOverview(
            @PathVariable Long tenantId,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        Map<String, Object> tenant = jdbcTemplate.queryForMap(
                """
                SELECT id, code AS tenantCode, name AS tenantName, short_name AS companyShortName,
                       contact_person AS contactPerson, contact_phone AS contactPhone,
                       contact_email AS contactEmail, credit_code AS businessLicense,
                       invoice_title AS invoiceTitle, tax_number AS taxNumber,
                       service_manager_user_id AS serviceManagerUserId, status, remark
                FROM tenant
                WHERE id = ? AND deleted = 0
                """,
                tenantId
        );
        List<Map<String, Object>> contracts = jdbcTemplate.queryForList(
                """
                SELECT c.id,
                       c.code AS contractNo,
                       c.status,
                       c.start_date AS startDate,
                       c.end_date AS endDate,
                       c.rent_amount AS rentAmount,
                       c.deposit_amount AS depositAmount,
                       (SELECT GROUP_CONCAT(r.room_no ORDER BY cr.id SEPARATOR '、')
                          FROM contract_room cr
                          JOIN building_room r ON r.id = cr.room_id AND r.deleted = 0
                         WHERE cr.contract_id = c.id AND cr.deleted = 0) AS roomSummary
                FROM contract c
                WHERE c.tenant_id = ? AND c.deleted = 0
                ORDER BY c.updated_at DESC, c.id DESC
                """,
                tenantId
        );
        List<Map<String, Object>> recentBills = principal.permissions() != null
                && principal.permissions().contains("bill:view")
                ? jdbcTemplate.queryForList(
                """
                SELECT id,
                       code AS billNumber,
                       contract_id AS contractId,
                       bill_type AS billType,
                       title,
                       amount,
                       paid_amount AS paidAmount,
                       audit_status AS auditStatus,
                       pay_status AS payStatus,
                       due_date AS dueDate
                FROM bill
                WHERE tenant_id = ? AND deleted = 0
                ORDER BY updated_at DESC, id DESC
                LIMIT 20
                """,
                tenantId
        )
                : List.of();
        long activeContactCount = count(
                "SELECT COUNT(*) FROM tenant_contact WHERE tenant_id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')",
                tenantId
        );
        long boundContactCount = count(
                "SELECT COUNT(DISTINCT tenant_contact_id) FROM mobile_user_bind WHERE tenant_id = ? AND tenant_contact_id IS NOT NULL AND deleted = 0 AND status = 'ENABLED'",
                tenantId
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenant", tenant);
        response.put("contacts", tenantContacts(tenantId).data());
        response.put("contracts", contracts);
        response.put("recentBills", recentBills);
        response.put("miniProgramVisibility", Map.of(
                "hasActiveContact", activeContactCount > 0,
                "boundContactCount", boundContactCount,
                "visibleContactCount", activeContactCount
        ));
        return ApiResponse.success(response);
    }

    @GetMapping("/tenant-contacts")
    public ApiResponse<List<Map<String, Object>>> tenantContacts() {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT * FROM tenant_contact WHERE deleted = 0 ORDER BY id DESC"));
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }

    private void ensureTenantCanBeDeleted(Long tenantId) {
        List<String> blockers = new ArrayList<>();
        addBlocker(blockers, "有效合同", count(
                "SELECT COUNT(*) FROM contract WHERE tenant_id = ? AND deleted = 0 AND status NOT IN ('TERMINATED', 'CANCELLED', 'EXPIRED')",
                tenantId
        ));
        addBlocker(blockers, "未结束租约", count(
                "SELECT COUNT(*) FROM lease WHERE tenant_id = ? AND deleted = 0 AND status NOT IN ('TERMINATED', 'CANCELLED', 'EXPIRED')",
                tenantId
        ));
        addBlocker(blockers, "未结账单", count(
                "SELECT COUNT(*) FROM bill WHERE tenant_id = ? AND deleted = 0 AND pay_status NOT IN ('PAID', 'CANCELLED') AND status NOT IN ('CANCELLED', 'DELETED', 'VOID')",
                tenantId
        ));
        addBlocker(blockers, "有效访客预约", count(
                "SELECT COUNT(*) FROM visitor_appointment WHERE tenant_id = ? AND deleted = 0 AND appointment_status NOT IN ('CANCELLED', 'COMPLETED', 'REJECTED')",
                tenantId
        ));
        addBlocker(blockers, "有效会议预约", count(
                "SELECT COUNT(*) FROM meeting_booking WHERE tenant_id = ? AND deleted = 0 AND booking_status NOT IN ('CANCELLED', 'COMPLETED', 'REJECTED')",
                tenantId
        ));
        addBlocker(blockers, "有效停车绑定", count(
                "SELECT COUNT(*) FROM parking_assignment WHERE tenant_id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')",
                tenantId
        ));
        if (!blockers.isEmpty()) {
            throw new BusinessException(409, "租户仍有关联业务，不能删除：" + String.join("；", blockers));
        }
    }

    private void ensureTenantCanBeDeactivated(Long tenantId) {
        long activeContracts = count(
                """
                SELECT COUNT(*)
                FROM contract
                WHERE tenant_id = ?
                  AND deleted = 0
                  AND status = 'ACTIVE'
                  AND start_date <= CURRENT_DATE
                  AND end_date >= CURRENT_DATE
                """,
                tenantId
        );
        if (activeContracts > 0) {
            throw new BusinessException(409, "租户存在当前有效合同，不能停用");
        }
    }

    private void validateServiceManager(Long userId) {
        if (userId == null) return;
        long count = count(
                """
                SELECT COUNT(DISTINCT user_record.id)
                FROM sys_user user_record
                WHERE user_record.id = ?
                  AND user_record.deleted = 0
                  AND user_record.status IN ('ACTIVE', 'ENABLED')
                  AND EXISTS (
                      SELECT 1
                      FROM sys_user_role user_role
                      INNER JOIN sys_role role_record
                        ON role_record.id = user_role.role_id
                       AND role_record.deleted = 0
                       AND role_record.status IN ('ACTIVE', 'ENABLED')
                      LEFT JOIN sys_role_permission role_permission
                        ON role_permission.role_id = role_record.id
                       AND role_permission.deleted = 0
                       AND role_permission.status IN ('ACTIVE', 'ENABLED')
                      LEFT JOIN sys_permission permission_record
                        ON permission_record.id = role_permission.permission_id
                       AND permission_record.deleted = 0
                       AND permission_record.status IN ('ACTIVE', 'ENABLED')
                      WHERE user_role.user_id = user_record.id
                        AND user_role.deleted = 0
                        AND user_role.status IN ('ACTIVE', 'ENABLED')
                        AND (
                            role_record.code = 'SUPER_ADMIN'
                            OR permission_record.code IN ('tenant:view', 'tenant:update')
                        )
                  )
                """,
                userId
        );
        if (count == 0) {
            throw new BusinessException(400, "物业对接人必须是启用且具备租户档案权限的内部用户");
        }
    }

    private Map<String, Object> contactForUpdate(Long id, Long tenantId) {
        List<Object> args = new ArrayList<>();
        args.add(id);
        String tenantFilter = "";
        if (tenantId != null) {
            tenantFilter = " AND tenant_id = ?";
            args.add(tenantId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, tenant_id AS tenantId, role, is_primary AS isPrimary,
                       is_emergency AS isEmergency, status
                FROM tenant_contact
                WHERE id = ? AND deleted = 0
                """ + tenantFilter + " FOR UPDATE",
                args.toArray()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Contact not found");
        }
        return rows.getFirst();
    }

    private void ensureContactCanBeDisabled(Map<String, Object> contact) {
        boolean responsible = truthy(contact.get("isPrimary"))
                || "RESPONSIBLE".equalsIgnoreCase(String.valueOf(contact.get("role")));
        if (responsible && active(String.valueOf(contact.get("status")))) {
            throw new BusinessException(409, "不能停用或删除当前租户负责人，请先设置新的负责人");
        }
    }

    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value)
                || value instanceof Number number && number.intValue() != 0
                || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    private void setEmergencyContactInternal(Long tenantId, Long contactId, boolean emergency, Long operatorId) {
        if (emergency) {
            jdbcTemplate.update(
                    """
                    UPDATE tenant_contact
                    SET is_emergency = 0, updated_by = ?
                    WHERE tenant_id = ? AND id <> ? AND deleted = 0 AND is_emergency = 1
                    """,
                    operatorId,
                    tenantId,
                    contactId
            );
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE tenant_contact
                SET is_emergency = ?, updated_by = ?
                WHERE id = ? AND tenant_id = ? AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                """,
                emergency ? 1 : 0,
                operatorId,
                contactId,
                tenantId
        );
        if (emergency && updated == 0) {
            throw new BusinessException(409, "只有当前启用的租户联系人可以设为紧急联系人");
        }
        if (emergency) {
            auditContact(contactId, "TENANT_EMERGENCY_CONTACT_CHANGED", tenantId, operatorId);
        }
    }

    private void auditContact(Long contactId, String action, Long tenantId, Long operatorId) {
        if (domainEventRepository == null) return;
        domainEventRepository.audit(
                "TENANT_CONTACT",
                contactId,
                action,
                "{}",
                "{\"tenantId\":" + tenantId + ",\"contactId\":" + contactId + "}",
                operatorId,
                UUID.randomUUID().toString()
        );
    }

    private void auditTenantRelation(
            Long tenantId,
            String action,
            Long previousManagerId,
            Long nextManagerId,
            Long operatorId
    ) {
        if (domainEventRepository == null || tenantId == null) return;
        domainEventRepository.audit(
                "TENANT",
                tenantId,
                action,
                "{\"serviceManagerUserId\":" + jsonNumber(previousManagerId) + "}",
                "{\"serviceManagerUserId\":" + jsonNumber(nextManagerId) + "}",
                operatorId,
                UUID.randomUUID().toString()
        );
    }

    private String jsonNumber(Long value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private boolean active(String status) {
        return status != null && List.of("ACTIVE", "ENABLED").contains(status.trim().toUpperCase());
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private void addBlocker(List<String> blockers, String label, long count) {
        if (count > 0) {
            blockers.add(label + " " + count + " 条");
        }
    }
}

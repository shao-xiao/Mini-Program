package com.dehui.property.modules.tenant.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.ContactValidators;
import com.dehui.property.common.JdbcMaps;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TenantController {

    private final JdbcTemplate jdbcTemplate;

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
                       status,
                       remark
                FROM tenant
                WHERE deleted = 0
                ORDER BY updated_at DESC, id DESC
                """
        ));
    }

    @PostMapping("/tenant/save")
    public ApiResponse<Void> saveTenant(@RequestBody Map<String, Object> body) {
        String tenantName = JdbcMaps.requiredStr(body, "Tenant name is required", "tenantName", "name");
        Long id = JdbcMaps.longVal(body, "id");
        String status = JdbcMaps.strOr(body, "ACTIVE", "status");
        String contactPhone = ContactValidators.optionalPhone(JdbcMaps.str(body, "contactPhone", "phone"));
        String contactEmail = ContactValidators.optionalEmail(JdbcMaps.str(body, "contactEmail", "email"));
        String contactPerson = JdbcMaps.str(body, "contactPerson");
        try {
            if (id == null) {
                jdbcTemplate.update(
                        """
                        INSERT INTO tenant (code, name, contact_person, credit_code, contact_phone, contact_email, status, created_by, updated_by, remark)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        JdbcMaps.code("TEN"),
                        tenantName,
                        contactPerson,
                        JdbcMaps.str(body, "businessLicense", "creditCode"),
                        contactPhone,
                        contactEmail,
                        status,
                        0L,
                        0L,
                        JdbcMaps.str(body, "remark")
                );
            } else {
                int updated = jdbcTemplate.update(
                        """
                        UPDATE tenant
                        SET name = ?, contact_person = ?, credit_code = ?, contact_phone = ?, contact_email = ?, status = ?, updated_by = ?, remark = ?
                        WHERE id = ? AND deleted = 0
                        """,
                        tenantName,
                        contactPerson,
                        JdbcMaps.str(body, "businessLicense", "creditCode"),
                        contactPhone,
                        contactEmail,
                        status,
                        0L,
                        JdbcMaps.str(body, "remark"),
                        id
                );
                ensureUpdated(updated, "Tenant not found");
            }
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "Tenant already exists");
        }
    }

    @GetMapping("/tenant/{tenantId}/contacts")
    public ApiResponse<List<Map<String, Object>>> tenantContacts(@PathVariable Long tenantId) {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id,
                       tenant_id AS tenantId,
                       name,
                       phone,
                       email,
                       role,
                       is_primary AS isPrimary,
                       status,
                       remark
                FROM tenant_contact
                WHERE tenant_id = ? AND deleted = 0
                ORDER BY is_primary DESC, id DESC
                """,
                tenantId
        ));
    }

    @PostMapping("/tenant/{tenantId}/contacts")
    public ApiResponse<Map<String, Object>> saveTenantContact(@PathVariable Long tenantId, @RequestBody Map<String, Object> body) {
        String name = JdbcMaps.requiredStr(body, "Contact name is required", "name");
        String phone = ContactValidators.requiredPhone(JdbcMaps.str(body, "phone", "contactPhone"));
        String email = ContactValidators.optionalEmail(JdbcMaps.str(body, "email", "contactEmail"));
        Boolean primary = JdbcMaps.bool(body, false, "isPrimary");
        Long id = JdbcMaps.longVal(body, "id");

        if (Boolean.TRUE.equals(primary)) {
            jdbcTemplate.update("UPDATE tenant_contact SET is_primary = 0 WHERE tenant_id = ? AND deleted = 0", tenantId);
        }

        if (id == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO tenant_contact (code, tenant_id, name, phone, email, role, is_primary, status, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    JdbcMaps.code("TC"),
                    tenantId,
                    name,
                    phone,
                    email,
                    JdbcMaps.str(body, "role"),
                    Boolean.TRUE.equals(primary) ? 1 : 0,
                    JdbcMaps.strOr(body, "ACTIVE", "status"),
                    0L,
                    0L
            );
        } else {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE tenant_contact
                    SET name = ?, phone = ?, email = ?, role = ?, is_primary = ?, status = ?, updated_by = ?
                    WHERE id = ? AND tenant_id = ? AND deleted = 0
                    """,
                    name,
                    phone,
                    email,
                    JdbcMaps.str(body, "role"),
                    Boolean.TRUE.equals(primary) ? 1 : 0,
                    JdbcMaps.strOr(body, "ACTIVE", "status"),
                    0L,
                    id,
                    tenantId
            );
            ensureUpdated(updated, "Contact not found");
        }
        return ApiResponse.success(Map.of("initialPassword", "123456"));
    }

    @PostMapping("/tenant/contacts/{id}/deactivate")
    public ApiResponse<Void> deactivateContact(@PathVariable Long id) {
        int updated = jdbcTemplate.update("UPDATE tenant_contact SET status = 'INACTIVE', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id);
        ensureUpdated(updated, "Contact not found");
        return ApiResponse.success();
    }

    @PostMapping("/tenant/contacts/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetContactPassword(@PathVariable Long id) {
        int updated = jdbcTemplate.update("UPDATE tenant_contact SET updated_by = ? WHERE id = ? AND deleted = 0", 0L, id);
        ensureUpdated(updated, "Contact not found");
        return ApiResponse.success(Map.of("initialPassword", "123456"));
    }

    @GetMapping("/tenant/{tenantId}/overview")
    public ApiResponse<Map<String, Object>> tenantOverview(@PathVariable Long tenantId) {
        Map<String, Object> tenant = jdbcTemplate.queryForMap(
                """
                SELECT id, code AS tenantCode, name AS tenantName, contact_person AS contactPerson, contact_phone AS contactPhone, contact_email AS contactEmail,
                       credit_code AS businessLicense, status, remark
                FROM tenant WHERE id = ? AND deleted = 0
                """,
                tenantId
        );
        return ApiResponse.success(Map.of(
                "tenant", tenant,
                "contacts", tenantContacts(tenantId).data(),
                "contracts", List.of(),
                "recentBills", List.of(),
                "miniProgramVisibility", Map.of("hasActiveContact", true, "boundContactCount", 0, "visibleContactCount", 0)
        ));
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
}

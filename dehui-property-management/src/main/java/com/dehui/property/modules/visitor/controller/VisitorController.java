package com.dehui.property.modules.visitor.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldApplicationService;
import com.dehui.property.modules.customfield.application.ModuleFieldSubmissionValidator;
import com.dehui.property.modules.mobile.controller.MobileApiSupport;
import com.dehui.property.modules.mobile.service.MobileCustomFieldValueService;
import com.dehui.property.modules.mobile.service.MobileStaffFileService;
import com.dehui.property.modules.visitor.application.VisitorApprovalService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequiredArgsConstructor
public class VisitorController {

    private final JdbcTemplate jdbcTemplate;
    private CustomFieldProjectionService customFieldProjectionService;
    private MobileCustomFieldValueService mobileCustomFieldValueService;
    private MobileStaffFileService mobileStaffFileService;
    private CustomFieldApplicationService customFieldApplicationService;
    private ModuleFieldSubmissionValidator moduleFieldSubmissionValidator;
    private VisitorApprovalService visitorApprovalService;

    @Autowired(required = false)
    void setCustomFieldProjectionService(CustomFieldProjectionService service) {
        this.customFieldProjectionService = service;
    }

    @Autowired(required = false)
    void setMobileCustomFieldValueService(MobileCustomFieldValueService service) {
        this.mobileCustomFieldValueService = service;
    }

    @Autowired(required = false)
    void setMobileStaffFileService(MobileStaffFileService service) {
        this.mobileStaffFileService = service;
    }

    @Autowired(required = false)
    void setCustomFieldApplicationService(CustomFieldApplicationService service) {
        this.customFieldApplicationService = service;
    }

    @Autowired(required = false)
    void setModuleFieldSubmissionValidator(ModuleFieldSubmissionValidator service) {
        this.moduleFieldSubmissionValidator = service;
    }

    @Autowired(required = false)
    void setVisitorApprovalService(VisitorApprovalService service) {
        this.visitorApprovalService = service;
    }

    @GetMapping("/visitors")
    public ApiResponse<List<Map<String, Object>>> visitors() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, tenant_id AS tenantId, visitor_name AS visitorName, visitor_phone AS visitorPhone,
                       visit_company AS companyName,
                       car_plate_no AS carPlateNo, car_plate_no AS plateNumber,
                       DATE_FORMAT(visit_time, '%Y-%m-%d') AS visitDate,
                       TIME_FORMAT(visit_time, '%H:%i') AS visitTime,
                       visit_time AS visitAt, visit_reason AS visitReason,
                       appointment_status AS status, remark, created_at AS createdAt,
                       reviewed_by AS reviewedBy, reviewed_at AS reviewedAt,
                       review_remark AS reviewRemark,
                       qr_code_file_id AS qrCodeFileId,
                       CASE WHEN qr_code_file_id IS NULL THEN NULL
                            ELSE CONCAT('/api/files/', qr_code_file_id) END AS qrCodeUrl,
                       qr_uploaded_by AS qrUploadedBy, qr_uploaded_at AS qrUploadedAt
                FROM visitor_appointment
                WHERE deleted = 0
                ORDER BY CASE WHEN appointment_status = 'PENDING' THEN 0 ELSE 1 END,
                         created_at DESC, id DESC
                """
        ));
    }

    @GetMapping("/mobile/visitors")
    public ApiResponse<Map<String, Object>> mobileVisitors(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        String scopeWhere = " AND tenant_id = ? AND (? = 1 OR applicant_mobile_user_id = ?)";
        List<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        args.add(companyScope ? 1 : 0);
        args.add(principal.userId());
        if (statusFilter != null) {
            if ("EXPIRED".equals(statusFilter)) {
                scopeWhere += " AND visit_time < NOW() AND appointment_status IN ('PENDING','REGISTERED')";
            } else {
                scopeWhere += " AND appointment_status = ?";
                args.add(statusFilter);
            }
        }
        PageResponse<Map<String, Object>> resultPage = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT id,
                       code,
                       code AS invitationNumber,
                       visitor_name AS visitorName,
                       visitor_phone AS visitorPhone,
                       car_plate_no AS carPlateNo,
                       car_plate_no AS plateNumber,
                       visit_company AS visitCompany,
                       visit_company AS companyName,
                       visit_reason AS visitReason,
                       DATE_FORMAT(visit_time, '%Y-%m-%d') AS visitDate,
                       TIME_FORMAT(visit_time, '%H:%i') AS visitTime,
                       visit_time AS visitAt,
                       CASE
                         WHEN visit_time < NOW() AND appointment_status IN ('PENDING','REGISTERED') THEN 'EXPIRED'
                         ELSE appointment_status
                       END AS status,
                       remark,
                       reviewed_at AS reviewedAt,
                       review_remark AS reviewRemark,
                       qr_code_file_id AS qrCodeFileId,
                       CASE
                         WHEN qr_code_file_id IS NOT NULL
                          AND appointment_status IN ('REGISTERED','ENTERED','LEFT')
                         THEN CONCAT('/api/mobile/files/', qr_code_file_id)
                         ELSE NULL
                       END AS qrCodeUrl,
                       qr_uploaded_at AS qrUploadedAt,
                       created_at AS createdTime,
                       created_at AS createdAt
                FROM visitor_appointment
                WHERE deleted = 0
                """ + scopeWhere + "\n" + """
                ORDER BY visit_time DESC, id DESC
                """,
                "SELECT COUNT(*) FROM visitor_appointment WHERE deleted = 0" + scopeWhere,
                args,
                page,
                pageSize
        );
        List<Map<String, Object>> visitors = resultPage.records();
        if (customFieldProjectionService != null) {
            visitors = customFieldProjectionService.attach("visitors", visitors);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", MobileApiSupport.profile(jdbcTemplate, principal));
        response.put("visitors", visitors);
        response.put("records", visitors);
        response.put("total", resultPage.total());
        response.put("page", resultPage.page());
        response.put("pageSize", resultPage.pageSize());
        return ApiResponse.success(response);
    }

    public ApiResponse<Map<String, Object>> mobileVisitors(AuthPrincipal principal) {
        return mobileVisitors(principal, null, null, null);
    }

    @GetMapping("/mobile/visitors/{id}")
    public ApiResponse<Map<String, Object>> mobileVisitorDetail(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       code,
                       code AS invitationNumber,
                       visitor_name AS visitorName,
                       visitor_phone AS visitorPhone,
                       car_plate_no AS carPlateNo,
                       car_plate_no AS plateNumber,
                       visit_company AS visitCompany,
                       visit_company AS companyName,
                       visit_reason AS visitReason,
                       DATE_FORMAT(visit_time, '%Y-%m-%d') AS visitDate,
                       TIME_FORMAT(visit_time, '%H:%i') AS visitTime,
                       visit_time AS visitAt,
                       CASE
                         WHEN visit_time < NOW() AND appointment_status IN ('PENDING','REGISTERED') THEN 'EXPIRED'
                         ELSE appointment_status
                       END AS status,
                       remark,
                       reviewed_at AS reviewedAt,
                       review_remark AS reviewRemark,
                       qr_code_file_id AS qrCodeFileId,
                       CASE
                         WHEN qr_code_file_id IS NOT NULL
                          AND appointment_status IN ('REGISTERED','ENTERED','LEFT')
                         THEN CONCAT('/api/mobile/files/', qr_code_file_id)
                         ELSE NULL
                       END AS qrCodeUrl,
                       qr_uploaded_at AS qrUploadedAt,
                       created_at AS createdTime,
                       created_at AS createdAt
                FROM visitor_appointment
                WHERE id = ?
                  AND tenant_id = ?
                  AND (? = 1 OR applicant_mobile_user_id = ?)
                  AND deleted = 0
                """,
                id,
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "访客预约不存在或无权访问");
        }
        List<Map<String, Object>> decorated = customFieldProjectionService == null
                ? rows
                : customFieldProjectionService.attach("visitors", rows);
        return ApiResponse.success(decorated.getFirst());
    }

    @PostMapping("/visitors")
    @Transactional
    public ApiResponse<Void> create(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        String code = JdbcMaps.code("VIS");
        Map<String, Object> canonical = canonical(body);
        jdbcTemplate.update(
                """
                INSERT INTO visitor_appointment (
                    code, tenant_id, visitor_name, visitor_phone, car_plate_no,
                    visit_company, visit_reason, visit_time, appointment_status,
                    status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'ACTIVE', ?, ?, ?)
                """,
                code,
                JdbcMaps.longVal(canonical, "tenantId"),
                JdbcMaps.requiredStr(canonical, "Visitor name is required", "visitorName"),
                JdbcMaps.requiredStr(canonical, "Visitor phone is required", "visitorPhone"),
                JdbcMaps.str(canonical, "plateNumber"),
                JdbcMaps.str(canonical, "companyName"),
                JdbcMaps.str(canonical, "visitReason"),
                JdbcMaps.datetime(canonical, JdbcMaps.now(), "visitAt"),
                principal.userId(),
                principal.userId(),
                JdbcMaps.str(canonical, "remark")
        );
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM visitor_appointment WHERE code = ? AND deleted = 0 LIMIT 1", Long.class, code
        );
        if (customFieldApplicationService != null) {
            customFieldApplicationService.saveValues("visitors", id, map(canonical.get("customFields")), principal);
        }
        return ApiResponse.success();
    }

    @GetMapping("/visitors/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, code, visitor_name AS visitorName, visitor_phone AS visitorPhone,
                       visit_company AS companyName, car_plate_no AS plateNumber,
                       DATE_FORMAT(visit_time, '%Y-%m-%d') AS visitDate,
                       TIME_FORMAT(visit_time, '%H:%i') AS visitTime,
                       visit_reason AS visitReason, remark,
                       appointment_status AS status, created_at AS createdAt,
                       reviewed_by AS reviewedBy, reviewed_at AS reviewedAt,
                       review_remark AS reviewRemark,
                       qr_code_file_id AS qrCodeFileId,
                       CASE WHEN qr_code_file_id IS NULL THEN NULL
                            ELSE CONCAT('/api/files/', qr_code_file_id) END AS qrCodeUrl,
                       qr_uploaded_by AS qrUploadedBy, qr_uploaded_at AS qrUploadedAt
                FROM visitor_appointment WHERE id = ? AND deleted = 0
                """, id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "Visitor appointment not found");
        List<Map<String, Object>> decorated = customFieldProjectionService == null
                ? rows : customFieldProjectionService.attach("visitors", rows);
        return ApiResponse.success(decorated.getFirst());
    }

    @PutMapping("/visitors/{id}")
    @Transactional
    public ApiResponse<Void> update(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> canonical = canonical(body);
        int updated = jdbcTemplate.update(
                """
                UPDATE visitor_appointment
                SET visitor_name = ?, visitor_phone = ?, visit_company = ?, car_plate_no = ?,
                    visit_time = ?, visit_reason = ?, remark = ?, updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                JdbcMaps.requiredStr(canonical, "Visitor name is required", "visitorName"),
                JdbcMaps.requiredStr(canonical, "Visitor phone is required", "visitorPhone"),
                JdbcMaps.str(canonical, "companyName"),
                JdbcMaps.str(canonical, "plateNumber"),
                JdbcMaps.datetime(canonical, JdbcMaps.now(), "visitAt"),
                JdbcMaps.str(canonical, "visitReason"),
                JdbcMaps.str(canonical, "remark"),
                principal.userId(),
                id
        );
        if (updated == 0) throw new BusinessException(404, "Visitor appointment not found");
        if (customFieldApplicationService != null) {
            customFieldApplicationService.saveValues("visitors", id, map(canonical.get("customFields")), principal);
        }
        return ApiResponse.success();
    }

    @PostMapping("/mobile/visitors")
    @Transactional
    public ApiResponse<Map<String, Object>> mobileCreate(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        Map<String, Object> canonical = canonical(body);
        if (moduleFieldSubmissionValidator != null) {
            moduleFieldSubmissionValidator.validateMobileCreate(
                    "visitors", canonical, principal,
                    java.util.Set.of("name", "phone", "carPlateNo", "plateNo", "visitCompany",
                            "company", "reason", "appointmentTime", "visitAt")
            );
        }
        String code = JdbcMaps.code("VIS");
        jdbcTemplate.update(
                """
                INSERT INTO visitor_appointment
                   (code, tenant_id, applicant_mobile_user_id, visitor_name, visitor_phone,
                    car_plate_no, visit_company, visit_reason, visit_time,
                    appointment_status, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'ACTIVE', ?, ?, ?)
                """,
                code,
                tenantId,
                principal.userId(),
                JdbcMaps.requiredStr(canonical, "Visitor name is required", "visitorName"),
                JdbcMaps.requiredStr(canonical, "Visitor phone is required", "visitorPhone"),
                JdbcMaps.str(canonical, "plateNumber"),
                JdbcMaps.str(canonical, "companyName"),
                JdbcMaps.str(canonical, "visitReason"),
                JdbcMaps.datetime(canonical, JdbcMaps.now(), "visitAt"),
                0L,
                0L,
                JdbcMaps.str(canonical, "remark")
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM visitor_appointment WHERE code = ? AND deleted = 0 LIMIT 1", Long.class, code);
        if (mobileStaffFileService != null) {
            mobileStaffFileService.claimDraftFiles(
                    principal, fileIds(canonical.get("customFields")), "VISITOR", id, "visitors"
            );
        }
        if (mobileCustomFieldValueService != null) {
            mobileCustomFieldValueService.save("visitors", id, canonical.get("customFields"), principal.userId());
        }
        return ApiResponse.success(Map.of("id", id, "code", code));
    }

    @PostMapping("/mobile/visitors/{id}/cancel")
    public ApiResponse<Void> mobileCancel(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        int updated = jdbcTemplate.update(
                """
                UPDATE visitor_appointment
                SET appointment_status = 'CANCELLED', updated_by = ?
                WHERE id = ?
                  AND tenant_id = ?
                  AND (? = 1 OR applicant_mobile_user_id = ?)
                  AND deleted = 0
                  AND appointment_status IN ('PENDING', 'REGISTERED')
                """,
                0L,
                id,
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        );
        if (updated == 0) {
            throw new BusinessException(404, "Visitor appointment not found");
        }
        return ApiResponse.success();
    }

    @PostMapping("/mobile/visitors/{id}/verify")
    public ApiResponse<Void> mobileVerify(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        MobileApiSupport.requireCapability(principal, "visitor.staff.verify");
        Long staffUserId = MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
        int updated = jdbcTemplate.update(
                """
                UPDATE visitor_appointment
                SET appointment_status = 'ENTERED', updated_by = ?
                WHERE id = ?
                  AND deleted = 0
                  AND appointment_status = 'REGISTERED'
                """,
                staffUserId,
                id
        );
        if (updated == 0) {
            throw new BusinessException(404, "Visitor appointment not found or cannot be verified");
        }
        jdbcTemplate.update(
                """
                INSERT INTO visitor_pass_record
                  (appointment_id, pass_type, gate_name, operator_id, status, created_by, updated_by)
                VALUES (?, 'ENTER', ?, ?, 'ACTIVE', ?, ?)
                """,
                id,
                "mobile verification",
                staffUserId,
                staffUserId,
                staffUserId
        );
        return ApiResponse.success();
    }

    @PatchMapping("/visitors/{id}/enter")
    @Transactional
    public ApiResponse<Void> enter(@PathVariable Long id) {
        updateStatus(id, "REGISTERED", "ENTERED");
        pass(id, "ENTER");
        return ApiResponse.success();
    }

    @PatchMapping("/visitors/{id}/leave")
    @Transactional
    public ApiResponse<Void> leave(@PathVariable Long id) {
        updateStatus(id, "ENTERED", "LEFT");
        pass(id, "LEAVE");
        return ApiResponse.success();
    }

    @PatchMapping("/visitors/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        int updated = jdbcTemplate.update(
                """
                UPDATE visitor_appointment
                SET appointment_status = 'CANCELLED', updated_by = ?
                WHERE id = ? AND appointment_status IN ('PENDING', 'REGISTERED') AND deleted = 0
                """,
                0L,
                id
        );
        if (updated == 0) {
            throw new BusinessException(409, "当前预约状态不能取消");
        }
        return ApiResponse.success();
    }

    @PatchMapping("/visitors/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(approvalService().approve(id, reviewRemark(body), principal));
    }

    @PatchMapping("/visitors/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(approvalService().reject(id, reviewRemark(body), principal));
    }

    @PutMapping("/visitors/{id}/qr-code")
    public ApiResponse<Map<String, Object>> attachQrCode(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(approvalService().attachQrCode(
                id,
                JdbcMaps.longVal(body, "fileId"),
                principal
        ));
    }

    private VisitorApprovalService approvalService() {
        if (visitorApprovalService == null) {
            throw new BusinessException(500, "访客审核服务不可用");
        }
        return visitorApprovalService;
    }

    private String reviewRemark(Map<String, Object> body) {
        return body == null ? null : JdbcMaps.str(body, "reviewRemark");
    }

    private void updateStatus(Long id, String expectedStatus, String targetStatus) {
        int updated = jdbcTemplate.update(
                """
                UPDATE visitor_appointment
                SET appointment_status = ?, updated_by = ?
                WHERE id = ? AND appointment_status = ? AND deleted = 0
                """,
                targetStatus,
                0L,
                id,
                expectedStatus
        );
        if (updated == 0) {
            throw new BusinessException(409, "当前预约状态不能执行该操作");
        }
    }

    private void pass(Long id, String type) {
        jdbcTemplate.update(
                "INSERT INTO visitor_pass_record (appointment_id, pass_type, gate_name, operator_id, status, created_by, updated_by) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, type, "front desk", 0L, 0L, 0L
        );
    }

    private Map<String, Object> canonical(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>(body == null ? Map.of() : body);
        alias(result, "visitorName", "name");
        alias(result, "visitorPhone", "phone");
        alias(result, "companyName", "visitCompany", "company");
        alias(result, "plateNumber", "carPlateNo", "plateNo");
        alias(result, "visitReason", "reason");
        if (!result.containsKey("visitAt")) {
            String date = text(result.get("visitDate"));
            String time = text(result.get("visitTime"));
            if (date != null) result.put("visitAt", date + " " + (time == null ? "00:00" : time));
            else alias(result, "visitAt", "visitTime", "appointmentTime");
        }
        return result;
    }

    private void alias(Map<String, Object> body, String canonical, String... aliases) {
        if (body.containsKey(canonical)) return;
        for (String alias : aliases) {
            if (body.containsKey(alias)) {
                body.put(canonical, body.get(alias));
                return;
            }
        }
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Long> fileIds(Object value) {
        List<Long> result = new java.util.ArrayList<>();
        collectFileIds(value, result);
        return result.stream().distinct().toList();
    }

    private void collectFileIds(Object value, List<Long> result) {
        if (value instanceof Map<?, ?> map) {
            Object fileId = map.get("fileId");
            if (fileId != null) {
                try {
                    long id = Long.parseLong(String.valueOf(fileId));
                    if (id > 0) result.add(id);
                } catch (NumberFormatException ignored) {
                    // Legacy URL-only files remain readable.
                }
            }
            map.values().forEach(item -> collectFileIds(item, result));
        } else if (value instanceof Iterable<?> items) {
            items.forEach(item -> collectFileIds(item, result));
        }
    }
}

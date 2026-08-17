package com.dehui.property.modules.workorder.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.PageResponse;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldApplicationService;
import com.dehui.property.modules.customfield.application.ModuleFieldSubmissionValidator;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.mobile.controller.MobileApiSupport;
import com.dehui.property.modules.mobile.service.MobileCustomFieldValueService;
import com.dehui.property.modules.mobile.service.MobileStaffFileService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class WorkOrderController {

    private static final Set<String> ADJUSTABLE_STATUS = Set.of("CREATED", "SUBMITTED", "ASSIGNED", "PROCESSING", "COMPLETED", "CLOSED", "CANCELLED");
    private static final Map<String, String> MOBILE_CATEGORY_LABELS = Map.of(
            "WATER", "水路",
            "ELECTRIC", "电路",
            "AIR_CONDITIONER", "空调",
            "DOOR_WINDOW", "门窗",
            "NETWORK", "网络",
            "CLEANING", "保洁",
            "OTHER", "其他"
    );
    private static final Map<String, String> ORDERABLE_FIELDS = Map.ofEntries(
            Map.entry("orderNumber", "w.code"),
            Map.entry("title", "w.title"),
            Map.entry("category", "w.category"),
            Map.entry("status", "w.work_status"),
            Map.entry("priority", "w.priority"),
            Map.entry("tenant", "t.name"),
            Map.entry("companyName", "w.reported_company_name"),
            Map.entry("reportedAt", "w.reported_at"),
            Map.entry("handler", "u.real_name"),
            Map.entry("location", "COALESCE(r.room_no, w.remark)"),
            Map.entry("createdTime", "w.created_at"),
            Map.entry("updatedTime", "w.updated_at")
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;
    private MobileCustomFieldValueService mobileCustomFieldValueService;
    private MobileStaffFileService mobileStaffFileService;
    private CustomFieldApplicationService customFieldApplicationService;
    private ModuleFieldSubmissionValidator moduleFieldSubmissionValidator;

    public WorkOrderController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
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

    @GetMapping("/workorders")
    public ApiResponse<PageResponse<Map<String, Object>>> workorders(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String tenantKeyword,
            @RequestParam(required = false) Long handlerId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        ensureCanViewWorkOrders(principal);
        StringBuilder sql = new StringBuilder("""
                SELECT w.id,
                       w.code,
                       w.code AS orderNumber,
                       w.tenant_id AS tenantId,
                       t.name AS tenantName,
                       w.room_id AS roomId,
                       r.room_no AS roomName,
                       COALESCE(NULLIF(w.location, ''), r.room_no) AS location,
                       COALESCE(NULLIF(w.order_type, ''), 'REPAIR') AS orderType,
                       w.title,
                       w.category,
                       w.category AS problemCategory,
                       w.priority,
                       w.work_status AS status,
                       w.assignee_id AS assigneeId,
                       w.assignee_id AS handlerId,
                       u.real_name AS handlerName,
                       COALESCE(NULLIF(w.reporter_name, ''), tc.name, t.contact_person, mu.nickname, creator.real_name, creator.username) AS reporterName,
                       COALESCE(NULLIF(w.reporter_name, ''), tc.name, t.contact_person, mu.nickname, creator.real_name, creator.username) AS contactName,
                       COALESCE(NULLIF(w.reporter_phone, ''), tc.phone, t.contact_phone, mu.phone) AS reporterPhone,
                       COALESCE(NULLIF(w.reporter_phone, ''), tc.phone, t.contact_phone, mu.phone) AS contactPhone,
                       COALESCE(NULLIF(w.reported_company_name, ''), t.name) AS companyName,
                       w.reported_at AS reportedAt,
                       w.created_by AS reporterId,
                       w.submitter_mobile_user_id AS mobileUserId,
                       w.created_at AS createdTime,
                       w.created_at AS submittedTime,
                       w.updated_at AS updatedTime,
                       w.remark,
                       COALESCE(NULLIF(w.description, ''), w.remark) AS description,
                       (
                         SELECT br.id
                         FROM bill br
                         WHERE br.source_type = 'WORK_ORDER'
                           AND br.source_id = w.id
                           AND br.deleted = 0
                         ORDER BY br.id DESC
                         LIMIT 1
                       ) AS billId
                FROM work_order w
                LEFT JOIN tenant t ON t.id = w.tenant_id AND t.deleted = 0
                LEFT JOIN building_room r ON r.id = w.room_id AND r.deleted = 0
                LEFT JOIN sys_user u ON u.id = w.assignee_id AND u.deleted = 0
                LEFT JOIN sys_user creator ON creator.id = w.created_by AND creator.deleted = 0
                LEFT JOIN mobile_user mu ON mu.id = w.submitter_mobile_user_id AND mu.deleted = 0
                LEFT JOIN mobile_user_bind mub ON mub.id = (
                    SELECT b.id
                    FROM mobile_user_bind b
                    WHERE b.mobile_user_id = w.submitter_mobile_user_id
                      AND b.bind_type = 'TENANT'
                      AND b.status = 'ENABLED'
                      AND b.deleted = 0
                    ORDER BY b.id DESC
                    LIMIT 1
                )
                LEFT JOIN tenant_contact tc ON tc.id = mub.tenant_contact_id AND tc.deleted = 0
                WHERE w.deleted = 0
                """);
        StringBuilder countSql = new StringBuilder("""
                SELECT COUNT(*)
                FROM work_order w
                LEFT JOIN tenant t ON t.id = w.tenant_id AND t.deleted = 0
                LEFT JOIN building_room r ON r.id = w.room_id AND r.deleted = 0
                LEFT JOIN sys_user u ON u.id = w.assignee_id AND u.deleted = 0
                WHERE w.deleted = 0
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();

        if (!canManageAllWorkOrders(principal)) {
            append(sql, countSql, " AND w.assignee_id = ?");
            args.add(principal.userId());
        }

        if (keyword != null && !keyword.isBlank()) {
            append(sql, countSql, " AND (w.code LIKE ? OR w.title LIKE ? OR w.remark LIKE ? OR r.room_no LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value);
            args.add(value);
            args.add(value);
            args.add(value);
        }
        if (category != null && !category.isBlank()) {
            append(sql, countSql, " AND w.category = ?");
            args.add(category);
        }
        if (tenantId != null) {
            append(sql, countSql, " AND w.tenant_id = ?");
            args.add(tenantId);
        }
        if (tenantKeyword != null && !tenantKeyword.isBlank()) {
            append(sql, countSql, " AND (t.name LIKE ? OR t.code LIKE ? OR w.tenant_id = ?)");
            String value = "%" + tenantKeyword.trim() + "%";
            args.add(value);
            args.add(value);
            args.add(parseLongOrMinusOne(tenantKeyword));
        }
        Long resolvedHandlerId = handlerId == null ? assigneeId : handlerId;
        if (resolvedHandlerId != null) {
            append(sql, countSql, " AND w.assignee_id = ?");
            args.add(resolvedHandlerId);
        }
        if (status != null && !status.isBlank()) {
            append(sql, countSql, " AND w.work_status = ?");
            args.add(status);
        }
        if (startTime != null && !startTime.isBlank()) {
            append(sql, countSql, " AND w.created_at >= ?");
            args.add(startTime.substring(0, Math.min(startTime.length(), 10)) + " 00:00:00");
        }
        if (endTime != null && !endTime.isBlank()) {
            append(sql, countSql, " AND w.created_at <= ?");
            args.add(endTime.substring(0, Math.min(endTime.length(), 10)) + " 23:59:59");
        }

        CompiledQuery customQuery = compileCustomQuery(customFilters, groupBy, sorts);
        if (customQuery != null && customQuery.hasPredicates()) {
            append(sql, countSql, " AND " + customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(ORDERABLE_FIELDS, groupBy, sorts, null, null, "w.updated_at", "w.id")
                : String.join(", ", customQuery.orderExpressions());
        sql.append(" ORDER BY ").append(orderBy);
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                sql.toString(),
                countSql.toString(),
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("workorders", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> workorders(
            AuthPrincipal principal,
            String keyword,
            String category,
            Long tenantId,
            String tenantKeyword,
            Long handlerId,
            Long assigneeId,
            String status,
            String startTime,
            String endTime,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return workorders(
                principal, keyword, category, tenantId, tenantKeyword, handlerId, assigneeId,
                status, startTime, endTime, null, sorts, groupBy, page, pageSize
        );
    }

    public ApiResponse<PageResponse<Map<String, Object>>> workorders(
            AuthPrincipal principal,
            String keyword,
            String category,
            Long tenantId,
            String tenantKeyword,
            Long handlerId,
            Long assigneeId,
            String status,
            String startTime,
            String endTime,
            Integer page,
            Integer pageSize) {
        return workorders(
                principal,
                keyword,
                category,
                tenantId,
                tenantKeyword,
                handlerId,
                assigneeId,
                status,
                startTime,
                endTime,
                null,
                null,
                null,
                page,
                pageSize
        );
    }

    private CompiledQuery compileCustomQuery(String customFilters, String groupBy, String sorts) {
        if (customFieldQueryService == null) return null;
        return customFieldQueryService.compile(
                "workorders",
                customFilters,
                groupBy,
                sorts,
                NativeOrderPolicy.of(ORDERABLE_FIELDS, null, null, "w.updated_at", "w.id")
        );
    }

    @GetMapping("/mobile/workorders")
    public ApiResponse<Map<String, Object>> mobileWorkorders(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        Map<String, Object> profile = MobileApiSupport.profile(jdbcTemplate, principal);
        boolean staffScope = principal != null && "STAFF".equals(principal.userType());
        Long staffUserId = staffScope ? MobileApiSupport.requireStaffUserId(jdbcTemplate, principal) : null;
        Long tenantId = staffScope ? null : MobileApiSupport.requireTenantId(principal);
        boolean companyScope = !staffScope && MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        List<Object> args = new java.util.ArrayList<>(List.of(
                staffScope ? 1 : 0,
                staffUserId == null ? 0L : staffUserId,
                staffScope ? 1 : 0,
                tenantId == null ? 0L : tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        ));
        String statusWhere = "";
        if (statusFilter != null) {
            statusWhere = " AND w.work_status = ?";
            args.add(statusFilter);
        }
        PageResponse<Map<String, Object>> resultPage = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT w.id,
                       w.code,
                       w.code AS orderNumber,
                       w.title,
                       w.category,
                       w.category AS categoryText,
                       w.priority,
                       w.priority AS priorityText,
                       w.work_status AS status,
                       w.work_status AS statusText,
                       u.real_name AS handlerName,
                       COALESCE(NULLIF(w.reporter_name, ''), tc.name, t.contact_person, mu.nickname) AS reporterName,
                       COALESCE(NULLIF(w.reporter_phone, ''), tc.phone, t.contact_phone, mu.phone) AS reporterPhone,
                       COALESCE(NULLIF(w.reported_company_name, ''), t.name) AS companyName,
                       w.reported_at AS reportedAt,
                       w.created_at AS createdTime,
                       w.created_at AS submittedTime,
                       (
                         SELECT r.created_at
                         FROM work_order_record r
                         WHERE r.work_order_id = w.id AND r.action = 'ASSIGNED' AND r.deleted = 0
                         ORDER BY r.id DESC
                         LIMIT 1
                       ) AS assignedTime,
                       (
                         SELECT r.created_at
                         FROM work_order_record r
                         WHERE r.work_order_id = w.id AND r.action = 'PROCESSING' AND r.deleted = 0
                         ORDER BY r.id DESC
                         LIMIT 1
                       ) AS processingTime,
                       (
                         SELECT r.created_at
                         FROM work_order_record r
                         WHERE r.work_order_id = w.id AND r.action = 'COMPLETED' AND r.deleted = 0
                         ORDER BY r.id DESC
                         LIMIT 1
                       ) AS completedTime,
                       (
                         SELECT r.created_at
                         FROM work_order_record r
                         WHERE r.work_order_id = w.id AND r.action = 'CLOSED' AND r.deleted = 0
                         ORDER BY r.id DESC
                         LIMIT 1
                       ) AS closedTime,
                       (
                         SELECT r.created_at
                         FROM work_order_record r
                         WHERE r.work_order_id = w.id AND r.action = 'CANCELLED' AND r.deleted = 0
                         ORDER BY r.id DESC
                         LIMIT 1
                       ) AS cancelledTime,
                       w.updated_at AS updatedTime,
                       w.order_type AS orderType,
                       w.location,
                       w.description,
                       w.remark,
                       (
                         SELECT GROUP_CONCAT(
                           CASE
                             WHEN f.access_scope = 'INTERNAL' THEN CONCAT('/api/mobile/files/', f.id)
                             ELSE f.storage_path
                           END
                           ORDER BY f.id ASC
                         )
                         FROM work_order_attachment a
                         INNER JOIN file_upload f ON f.id = a.file_id AND f.deleted = 0
                         WHERE a.work_order_id = w.id AND a.deleted = 0
                       ) AS imageUrlText
                FROM work_order w
                LEFT JOIN tenant t ON t.id = w.tenant_id AND t.deleted = 0
                LEFT JOIN sys_user u ON u.id = w.assignee_id AND u.deleted = 0
                LEFT JOIN mobile_user mu ON mu.id = w.submitter_mobile_user_id AND mu.deleted = 0
                LEFT JOIN mobile_user_bind mub ON mub.id = (
                    SELECT b.id
                    FROM mobile_user_bind b
                    WHERE b.mobile_user_id = w.submitter_mobile_user_id
                      AND b.bind_type = 'TENANT'
                      AND b.status = 'ENABLED'
                      AND b.deleted = 0
                    ORDER BY b.id DESC
                    LIMIT 1
                )
                LEFT JOIN tenant_contact tc ON tc.id = mub.tenant_contact_id AND tc.deleted = 0
                WHERE w.deleted = 0
                  AND (
                    (? = 1 AND w.assignee_id = ?)
                    OR
                    (? = 0 AND w.tenant_id = ? AND (? = 1 OR w.submitter_mobile_user_id = ?))
                  )
                """ + statusWhere + "\n" + """
                ORDER BY w.updated_at DESC, w.id DESC
                """,
                """
                SELECT COUNT(*)
                FROM work_order w
                WHERE w.deleted = 0
                  AND (
                    (? = 1 AND w.assignee_id = ?)
                    OR
                    (? = 0 AND w.tenant_id = ? AND (? = 1 OR w.submitter_mobile_user_id = ?))
                  )
                """ + statusWhere,
                args,
                page,
                pageSize
        );
        List<Map<String, Object>> workOrders = resultPage.records();
        workOrders.forEach(this::normalizeMobileWorkOrder);
        if (customFieldProjectionService != null) {
            workOrders = customFieldProjectionService.attach("workorders", workOrders);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", profile);
        response.put("workOrders", workOrders);
        response.put("records", workOrders);
        response.put("total", resultPage.total());
        response.put("page", resultPage.page());
        response.put("pageSize", resultPage.pageSize());
        response.put("notifications", List.of());
        response.put("unreadNotificationCount", 0);
        return ApiResponse.success(response);
    }

    public ApiResponse<Map<String, Object>> mobileWorkorders(AuthPrincipal principal) {
        return mobileWorkorders(principal, null, null, null);
    }

    @GetMapping("/mobile/workorders/{id}")
    public ApiResponse<Map<String, Object>> mobileWorkOrderDetail(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        MobileApiSupport.requireCapability(
                principal,
                principal != null && "STAFF".equals(principal.userType())
                        ? "workorder.staff.view"
                        : "workorder.view"
        );
        if (!hasMobileWorkOrderAccess(principal, id)) {
            throw new BusinessException(404, "工单不存在或无权访问");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT w.id,
                       w.code AS orderNumber,
                       w.title,
                       w.order_type AS orderType,
                       w.category,
                       w.priority,
                       w.work_status AS status,
                       w.location,
                       w.description,
                       w.reporter_name AS reporterName,
                       w.reporter_phone AS reporterPhone,
                       COALESCE(NULLIF(w.reported_company_name, ''), tenant_record.name) AS companyName,
                       w.reported_at AS reportedAt,
                       u.real_name AS handlerName,
                       w.created_at AS createdTime,
                       w.updated_at AS updatedTime,
                       w.remark
                FROM work_order w
                LEFT JOIN tenant tenant_record ON tenant_record.id = w.tenant_id AND tenant_record.deleted = 0
                LEFT JOIN sys_user u ON u.id = w.assignee_id AND u.deleted = 0
                WHERE w.id = ? AND w.deleted = 0
                """,
                id
        );
        Map<String, Object> detail = rows.getFirst();
        List<Map<String, Object>> attachments = jdbcTemplate.queryForList(
                """
                SELECT file_record.id,
                       file_record.original_name AS name,
                       attachment.attachment_type AS attachmentType,
                       CASE
                         WHEN file_record.access_scope = 'INTERNAL' THEN CONCAT('/api/mobile/files/', file_record.id)
                         ELSE file_record.storage_path
                       END AS url,
                       file_record.mime_type AS mimeType,
                       file_record.file_size AS fileSize
                FROM work_order_attachment attachment
                JOIN file_upload file_record ON file_record.id = attachment.file_id AND file_record.deleted = 0
                WHERE attachment.work_order_id = ?
                  AND attachment.work_order_record_id IS NULL
                  AND attachment.deleted = 0
                ORDER BY attachment.id
                """,
                id
        );
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                """
                SELECT record.id,
                       record.action,
                       record.content,
                       user_record.real_name AS operatorName,
                       record.created_at AS createdTime
                FROM work_order_record record
                LEFT JOIN sys_user user_record ON user_record.id = record.operator_id AND user_record.deleted = 0
                WHERE record.work_order_id = ? AND record.deleted = 0
                ORDER BY record.id
                """,
                id
        );
        records.forEach(record -> record.put("attachments", jdbcTemplate.queryForList(
                """
                SELECT file_record.id,
                       file_record.original_name AS name,
                       attachment.attachment_type AS attachmentType,
                       CASE
                         WHEN file_record.access_scope = 'INTERNAL' THEN CONCAT('/api/mobile/files/', file_record.id)
                         ELSE file_record.storage_path
                       END AS url,
                       file_record.mime_type AS mimeType,
                       file_record.file_size AS fileSize
                FROM work_order_attachment attachment
                JOIN file_upload file_record
                  ON file_record.id = attachment.file_id AND file_record.deleted = 0
                WHERE attachment.work_order_record_id = ?
                  AND attachment.deleted = 0
                ORDER BY attachment.id
                """,
                record.get("id")
        )));
        detail.put("attachments", attachments);
        detail.put("images", attachments.stream()
                .filter(this::isImageAttachment)
                .toList());
        detail.put("records", records);
        List<Map<String, Object>> decorated = customFieldProjectionService == null
                ? rows
                : customFieldProjectionService.attach("workorders", rows);
        return ApiResponse.success(decorated.getFirst());
    }

    private boolean isImageAttachment(Map<String, Object> attachment) {
        String mimeType = String.valueOf(attachment.getOrDefault("mimeType", ""));
        String attachmentType = String.valueOf(attachment.getOrDefault("attachmentType", ""));
        return mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")
                || Set.of("IMAGE", "PROCESS_IMAGE").contains(attachmentType);
    }

    @PostMapping("/mobile/workorders/notifications/{id}/read")
    public ApiResponse<Void> mobileReadNotification(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        Long staffUserId = MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
        int updated = jdbcTemplate.update(
                """
                UPDATE work_order_mobile_notification
                SET read_status = 'READ', read_at = NOW(), updated_by = ?
                WHERE id = ? AND recipient_user_id = ? AND deleted = 0
                """,
                staffUserId,
                id,
                staffUserId
        );
        if (updated == 0) {
            throw new BusinessException(404, "通知不存在");
        }
        return ApiResponse.success();
    }

    @PostMapping("/mobile/workorders")
    @Transactional
    // Mobile relationship fields remain server-derived; reporter/company are display snapshots only.
    public ApiResponse<Map<String, Object>> mobileCreateWorkOrder(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        Map<String, Object> canonical = new LinkedHashMap<>(body == null ? Map.of() : body);
        copyAlias(canonical, "problemCategory", "category", "type");
        copyAlias(canonical, "description", "content");
        if (moduleFieldSubmissionValidator != null) {
            moduleFieldSubmissionValidator.validateMobileCreate(
                    "workorders", canonical, principal,
                    Set.of("roomId", "category", "type", "content", "title", "orderType", "priority", "location")
            );
        }
        String code = JdbcMaps.code("WO");
        String location = JdbcMaps.strOr(canonical, "", "location");
        String description = JdbcMaps.requiredStr(canonical, "问题描述不能为空", "description");
        String remark = JdbcMaps.str(canonical, "remark");
        String problemCategory = JdbcMaps.strOr(canonical, "OTHER", "problemCategory");
        java.sql.Timestamp reportedAt = reportedAt(canonical);
        Map<String, String> reporter = resolveReporter(principal, tenantId, canonical);
        jdbcTemplate.update(
                """
                INSERT INTO work_order (
                    code, tenant_id, room_id, submitter_mobile_user_id,
                    reporter_name, reporter_phone, reported_company_name, reported_at,
                    title, order_type, category,
                    priority, location, description, work_status, status,
                    created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'REPAIR', ?, 'NORMAL', ?, ?, 'CREATED', 'ACTIVE', ?, ?, ?)
                """,
                code,
                tenantId,
                JdbcMaps.longVal(canonical, "roomId"),
                principal.userId(),
                reporter.get("name"),
                reporter.get("phone"),
                reporter.get("companyName"),
                reportedAt,
                mobileWorkOrderTitle(problemCategory, description),
                problemCategory,
                location,
                description,
                0L,
                0L,
                remark
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM work_order WHERE code = ? AND deleted = 0 LIMIT 1", Long.class, code);
        record(id, "CREATED", description, actorId(principal));
        syncMobileWorkOrderAttachments(principal, id, canonical);
        if (mobileStaffFileService != null) {
            mobileStaffFileService.claimDraftFiles(
                    principal, fileIds(canonical.get("customFields")), "WORK_ORDER", id, "workorders"
            );
        }
        if (mobileCustomFieldValueService != null) {
            mobileCustomFieldValueService.save("workorders", id, canonical.get("customFields"), principal.userId());
        }
        return ApiResponse.success(Map.of("id", id, "orderNumber", code));
    }

    @PostMapping("/mobile/workorders/{id}/cancel")
    public ApiResponse<Void> mobileCancelWorkOrder(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        int updated = jdbcTemplate.update(
                """
                UPDATE work_order
                SET work_status = 'CANCELLED', updated_by = ?
                WHERE id = ?
                  AND tenant_id = ?
                  AND (? = 1 OR submitter_mobile_user_id = ?)
                  AND deleted = 0
                  AND work_status IN ('CREATED', 'SUBMITTED')
                """,
                0L,
                id,
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        );
        if (updated == 0) {
            throw new BusinessException(404, "Work order not found or cannot be cancelled");
        }
        record(id, "CANCELLED", null, actorId(principal));
        return ApiResponse.success();
    }

    @PostMapping("/mobile/workorders/{id}/evaluation")
    public ApiResponse<Void> mobileEvaluateWorkOrder(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM work_order
                WHERE id = ?
                  AND tenant_id = ?
                  AND (? = 1 OR submitter_mobile_user_id = ?)
                  AND deleted = 0
                  AND work_status IN ('COMPLETED', 'CLOSED')
                """,
                Long.class,
                id,
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        );
        if (count == null || count == 0) {
            throw new BusinessException(404, "Work order not found");
        }
        record(id, "EVALUATION", "rating=" + JdbcMaps.intVal(body, 5, "rating") + "; " + JdbcMaps.strOr(body, "", "content", "remark"), actorId(principal));
        return ApiResponse.success();
    }

    @PostMapping("/mobile/workorders/{id}/start")
    @Transactional
    public ApiResponse<Void> mobileStartWorkOrder(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        MobileApiSupport.requireCapability(principal, "workorder.staff.start");
        Long staffUserId = MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
        ensureMobileAssignedWorkOrder(staffUserId, id);
        int updated = jdbcTemplate.update(
                """
                UPDATE work_order
                SET work_status = 'PROCESSING', updated_by = ?
                WHERE id = ? AND assignee_id = ? AND work_status = 'ASSIGNED' AND deleted = 0
                """,
                staffUserId,
                id,
                staffUserId
        );
        if (updated == 0) {
            String current = currentMobileWorkOrderStatus(staffUserId, id);
            if (!"PROCESSING".equals(current)) {
                throw new BusinessException(409, "工单当前状态不能开始处理");
            }
            return ApiResponse.success();
        }
        record(id, "PROCESSING", null, staffUserId);
        return ApiResponse.success();
    }

    @PostMapping("/mobile/workorders/{id}/complete")
    @Transactional
    public ApiResponse<Void> mobileCompleteWorkOrder(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        MobileApiSupport.requireCapability(principal, "workorder.staff.complete");
        Long staffUserId = MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
        ensureMobileAssignedWorkOrder(staffUserId, id);
        String result = JdbcMaps.requiredStr(body, "处理结果不能为空", "result", "remark");
        int updated = jdbcTemplate.update(
                """
                UPDATE work_order
                SET work_status = 'COMPLETED', updated_by = ?
                WHERE id = ? AND assignee_id = ? AND work_status = 'PROCESSING' AND deleted = 0
                """,
                staffUserId,
                id,
                staffUserId
        );
        if (updated == 0) {
            String current = currentMobileWorkOrderStatus(staffUserId, id);
            if (!"COMPLETED".equals(current)) {
                throw new BusinessException(409, "工单必须处于处理中才能完成");
            }
            return ApiResponse.success();
        }
        record(id, "COMPLETED", result, staffUserId);
        return ApiResponse.success();
    }

    @PostMapping("/mobile/workorders/{id}/images")
    public ApiResponse<Map<String, Object>> mobileUploadWorkOrderImage(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws Exception {
        if (!hasMobileWorkOrderAccess(principal, id)) {
            throw new BusinessException(404, "Work order not found");
        }
        if (principal != null && "STAFF".equals(principal.userType())) {
            if (mobileStaffFileService == null) {
                throw new BusinessException(503, "内部附件服务暂不可用");
            }
            Long staffUserId = MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
            Map<String, Object> uploaded = mobileStaffFileService.uploadDraft(principal, file);
            Long fileId = ((Number) uploaded.get("fileId")).longValue();
            mobileStaffFileService.claimDraftFiles(List.of(fileId), "WORK_ORDER", id, staffUserId);
            jdbcTemplate.update(
                    """
                    INSERT INTO work_order_attachment (
                        work_order_id, file_id, attachment_type, status, created_by, updated_by
                    )
                    VALUES (?, ?, 'IMAGE', 'ENABLED', ?, ?)
                    """,
                    id,
                    fileId,
                    staffUserId,
                    staffUserId
            );
            return ApiResponse.success(uploaded);
        }
        String fileCode = JdbcMaps.code("FILE");
        String ext = extension(file.getOriginalFilename());
        String storedName = fileCode + ext;
        Path directory = Path.of("uploads", "public", "workorders");
        Files.createDirectories(directory);
        Path target = directory.resolve(storedName);
        file.transferTo(target);
        String publicPath = "/files/public/workorders/" + storedName;
        jdbcTemplate.update(
                """
                INSERT INTO file_upload (code, biz_type, biz_id, original_name, stored_name, storage_path, mime_type, file_size, status, created_by, updated_by)
                VALUES (?, 'WORK_ORDER', ?, ?, ?, ?, ?, ?, 'ENABLED', ?, ?)
                """,
                fileCode,
                id,
                file.getOriginalFilename() == null ? storedName : file.getOriginalFilename(),
                storedName,
                publicPath,
                file.getContentType(),
                file.getSize(),
                0L,
                0L
        );
        Long fileId = jdbcTemplate.queryForObject("SELECT id FROM file_upload WHERE code = ? AND deleted = 0 LIMIT 1", Long.class, fileCode);
        jdbcTemplate.update(
                "INSERT INTO work_order_attachment (work_order_id, file_id, attachment_type, status, created_by, updated_by) VALUES (?, ?, 'IMAGE', 'ENABLED', ?, ?)",
                id,
                fileId,
                0L,
                0L
        );
        return ApiResponse.success(Map.of("id", fileId, "url", publicPath));
    }

    private boolean hasMobileWorkOrderAccess(AuthPrincipal principal, Long workOrderId) {
        if (principal != null && "STAFF".equals(principal.userType())) {
            MobileApiSupport.requireCapability(principal, "workorder.staff.view");
            Long staffUserId = MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM work_order WHERE id = ? AND assignee_id = ? AND deleted = 0",
                    Long.class,
                    workOrderId,
                    staffUserId
            );
            return count != null && count > 0;
        }
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM work_order
                WHERE id = ?
                  AND tenant_id = ?
                  AND (? = 1 OR submitter_mobile_user_id = ?)
                  AND deleted = 0
                """,
                Long.class,
                workOrderId,
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        );
        return count != null && count > 0;
    }

    @GetMapping("/workorders/assignable-users")
    public ApiResponse<List<Map<String, Object>>> assignableUsers() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT DISTINCT u.id, u.username, u.real_name AS realName
                FROM sys_user u
                INNER JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0 AND ur.status = 'ENABLED'
                INNER JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 'ENABLED'
                INNER JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0 AND rp.status = 'ENABLED'
                INNER JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.status = 'ENABLED'
                WHERE u.deleted = 0
                  AND u.status = 'ENABLED'
                  AND p.code IN ('workorder:complete', 'workorder:*')
                ORDER BY u.id ASC
                """
        ));
    }

    @PostMapping("/workorders")
    @Transactional
    public ApiResponse<Void> create(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        Long actorId = actorId(principal);
        Map<String, String> reporter = resolveInternalReporter(principal);
        String code = JdbcMaps.code("WO");
        jdbcTemplate.update(
                """
                INSERT INTO work_order (
                    code, tenant_id, room_id, reporter_name, reporter_phone, title,
                    order_type, category, priority, location, description,
                    work_status, assignee_id, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, 'ACTIVE', ?, ?, ?)
                """,
                code,
                JdbcMaps.longVal(body, "tenantId"),
                JdbcMaps.longVal(body, "roomId"),
                reporter.get("name"),
                reporter.get("phone"),
                JdbcMaps.requiredStr(body, "Work order title is required", "title", "content"),
                JdbcMaps.strOr(body, "REPAIR", "orderType"),
                JdbcMaps.strOr(body, "OTHER", "problemCategory", "category", "type"),
                JdbcMaps.strOr(body, "NORMAL", "priority"),
                JdbcMaps.str(body, "location"),
                JdbcMaps.str(body, "description"),
                JdbcMaps.longVal(body, "handlerId", "assigneeId"),
                actorId,
                actorId,
                JdbcMaps.str(body, "remark")
        );
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM work_order WHERE code = ? AND deleted = 0 LIMIT 1", Long.class, code
        );
        syncAdminWorkOrderAttachments(principal, id, body);
        if (customFieldApplicationService != null) {
            customFieldApplicationService.saveValues("workorders", id, map(body.get("customFields")), principal);
        }
        return ApiResponse.success();
    }

    @GetMapping("/workorders/{id}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        ensureCanViewWorkOrders(principal);
        boolean globalScope = canManageAllWorkOrders(principal);
        Long scopedUserId = principal == null || principal.userId() == null ? -1L : principal.userId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT w.id, w.code, w.title,
                       COALESCE(NULLIF(w.order_type, ''), 'REPAIR') AS orderType,
                       w.category AS problemCategory,
                       COALESCE(NULLIF(w.location, ''), r.room_no) AS location,
                       COALESCE(NULLIF(w.description, ''), w.remark) AS description,
                       w.priority, w.reporter_name AS reporterName, w.reporter_name AS contactName,
                       w.reporter_phone AS contactPhone,
                       COALESCE(NULLIF(w.reported_company_name, ''), tenant_record.name) AS companyName,
                       w.reported_at AS reportedAt,
                       w.assignee_id AS handlerId,
                       COALESCE(NULLIF(handler.real_name, ''), handler.username) AS handlerName,
                       w.work_status AS status, w.remark,
                       w.created_at AS createdTime, w.updated_at AS updatedTime
                FROM work_order w
                LEFT JOIN building_room r ON r.id = w.room_id AND r.deleted = 0
                LEFT JOIN tenant tenant_record ON tenant_record.id = w.tenant_id AND tenant_record.deleted = 0
                LEFT JOIN sys_user handler ON handler.id = w.assignee_id AND handler.deleted = 0
                WHERE w.id = ?
                  AND w.deleted = 0
                  AND (? = 1 OR w.assignee_id = ?)
                """,
                id,
                globalScope ? 1 : 0,
                scopedUserId
        );
        if (rows.isEmpty()) throw new BusinessException(404, "Work order not found");
        Map<String, Object> detail = rows.getFirst();
        detail.put("images", workOrderFileValue(id, "IMAGE"));
        detail.put("attachments", workOrderFileValue(id, "ATTACHMENT"));
        detail.put("records", workOrderHandlingRecords(id));
        detail.put("propertyCustomFieldKeys", propertyCustomFieldKeys(id));
        List<Map<String, Object>> decorated = customFieldProjectionService == null
                ? rows : customFieldProjectionService.attach("workorders", rows);
        return ApiResponse.success(decorated.getFirst());
    }

    @PutMapping("/workorders/{id}")
    @Transactional
    public ApiResponse<Void> update(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        int updated = jdbcTemplate.update(
                """
                UPDATE work_order SET title = ?, order_type = ?, category = ?, priority = ?,
                    location = ?, description = ?, remark = ?, updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                JdbcMaps.requiredStr(body, "Work order title is required", "title"),
                JdbcMaps.strOr(body, "REPAIR", "orderType"),
                JdbcMaps.strOr(body, "OTHER", "problemCategory", "category"),
                JdbcMaps.strOr(body, "NORMAL", "priority"),
                JdbcMaps.str(body, "location"),
                JdbcMaps.str(body, "description"),
                JdbcMaps.str(body, "remark"),
                actorId(principal),
                id
        );
        if (updated == 0) throw new BusinessException(404, "Work order not found");
        syncAdminWorkOrderAttachments(principal, id, body);
        if (customFieldApplicationService != null) {
            customFieldApplicationService.saveValues("workorders", id, map(body.get("customFields")), principal);
        }
        return ApiResponse.success();
    }

    @PatchMapping("/workorders/{id}/assign")
    public ApiResponse<Void> assign(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long handlerId = JdbcMaps.longVal(body, "handlerId", "assigneeId");
        if (handlerId == null) {
            throw new BusinessException(400, "请选择维修人员");
        }
        updateStatus(id, "ASSIGNED", handlerId, false, actorId(principal));
        notifyAssignee(id, handlerId, actorId(principal));
        return ApiResponse.success();
    }

    @PatchMapping("/workorders/{id}/start")
    public ApiResponse<Void> start(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        ensureCanOperateAssignedWorkOrder(principal, id);
        updateStatus(id, "PROCESSING", null, false, actorId(principal));
        return ApiResponse.success();
    }

    @PatchMapping("/workorders/{id}/complete")
    public ApiResponse<Void> complete(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        ensureCanOperateAssignedWorkOrder(principal, id);
        String result = JdbcMaps.requiredStr(body, "处理结果不能为空", "result", "handlingResult", "remark");
        updateStatus(id, "COMPLETED", null, false, actorId(principal), result);
        return ApiResponse.success();
    }

    @PatchMapping("/workorders/{id}/close")
    public ApiResponse<Void> close(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        updateStatus(id, "CLOSED", null, false, actorId(principal));
        return ApiResponse.success();
    }

    @PatchMapping("/workorders/{id}/status")
    public ApiResponse<Void> changeStatus(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String status = JdbcMaps.requiredStr(body, "工单状态不能为空", "status").trim().toUpperCase();
        if (!ADJUSTABLE_STATUS.contains(status)) {
            throw new BusinessException(400, "工单状态不合法");
        }
        Long handlerId = JdbcMaps.longVal(body, "handlerId", "assigneeId");
        boolean clearHandler = "CREATED".equals(status) || "SUBMITTED".equals(status);
        if ("ASSIGNED".equals(status) && handlerId == null) {
            throw new BusinessException(400, "派单状态必须指定维修人员");
        }
        updateStatus(id, status, handlerId, clearHandler, actorId(principal));
        if ("ASSIGNED".equals(status)) {
            notifyAssignee(id, handlerId, actorId(principal));
        }
        record(id, "STATUS_CHANGE", JdbcMaps.str(body, "remark"), actorId(principal));
        return ApiResponse.success();
    }

    @PostMapping("/workorders/{id}/generate-bill")
    public ApiResponse<Void> generateBill(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        Map<String, Object> order = jdbcTemplate.queryForMap("SELECT tenant_id FROM work_order WHERE id = ? AND deleted = 0", id);
        Object tenantId = order.get("tenant_id");
        if (tenantId == null) {
            throw new BusinessException(400, "Work order has no tenant");
        }
        jdbcTemplate.update(
                """
                INSERT INTO bill (code, tenant_id, source_type, source_id, bill_type, amount, paid_amount, due_date, audit_status, pay_status, status, created_by, updated_by)
                VALUES (?, ?, 'WORK_ORDER', ?, 'WORK_ORDER', 0, 0, ?, 'PENDING', 'UNPAID', 'ACTIVE', ?, ?)
                """,
                JdbcMaps.code("BIL"),
                tenantId,
                id,
                JdbcMaps.today(),
                0L,
                0L
        );
        return ApiResponse.success();
    }

    private void updateStatus(Long id, String status, Long assigneeId, boolean clearHandler, Long updatedBy) {
        updateStatus(id, status, assigneeId, clearHandler, updatedBy, null);
    }

    private void updateStatus(
            Long id,
            String status,
            Long assigneeId,
            boolean clearHandler,
            Long updatedBy,
            String recordContent
    ) {
        int updated;
        if (clearHandler) {
            updated = jdbcTemplate.update(
                    "UPDATE work_order SET work_status = ?, assignee_id = NULL, updated_by = ? WHERE id = ? AND deleted = 0",
                    status,
                    updatedBy,
                    id
            );
        } else if (assigneeId == null) {
            updated = jdbcTemplate.update(
                    "UPDATE work_order SET work_status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                    status,
                    updatedBy,
                    id
            );
        } else {
            updated = jdbcTemplate.update(
                    "UPDATE work_order SET work_status = ?, assignee_id = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                    status,
                    assigneeId,
                    updatedBy,
                    id
            );
        }
        if (updated == 0) {
            throw new BusinessException(404, "Work order not found");
        }
        record(id, status, recordContent, updatedBy);
    }

    private void record(Long id, String action, String content, Long operatorId) {
        jdbcTemplate.update(
                "INSERT INTO work_order_record (work_order_id, operator_id, action, content, status, created_by, updated_by) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, operatorId, action, content, operatorId, operatorId
        );
    }

    private void notifyAssignee(Long id, Long assigneeId, Long operatorId) {
        if (id == null || assigneeId == null) {
            return;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT w.code AS orderNumber,
                       w.title,
                       COALESCE(r.room_no, SUBSTRING_INDEX(w.remark, CHAR(10), 1), '-') AS location
                FROM work_order w
                LEFT JOIN building_room r ON r.id = w.room_id AND r.deleted = 0
                WHERE w.id = ? AND w.deleted = 0
                LIMIT 1
                """,
                id
        );
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.getFirst();
        String orderNumber = trimmed(row.get("orderNumber"));
        String title = trimmed(row.get("title"));
        String location = trimmed(row.get("location"));
        String notificationTitle = truncate("新派单：" + (orderNumber.isBlank() ? "工单" : orderNumber), 128);
        String content = truncate((title.isBlank() ? "请处理新工单" : title) + (location.isBlank() ? "" : "｜位置：" + location), 512);
        jdbcTemplate.update(
                """
                INSERT INTO work_order_mobile_notification
                  (work_order_id, recipient_user_id, action, title, content, read_status, status, created_by, updated_by)
                VALUES (?, ?, 'ASSIGNED', ?, ?, 'UNREAD', 'ACTIVE', ?, ?)
                """,
                id,
                assigneeId,
                notificationTitle,
                content,
                operatorId,
                operatorId
        );
    }

    private List<Map<String, Object>> mobileUnreadNotifications(Long staffUserId) {
        return jdbcTemplate.queryForList(
                """
                SELECT n.id,
                       n.work_order_id AS workOrderId,
                       w.code AS orderNumber,
                       w.title AS workOrderTitle,
                       n.title,
                       n.content,
                       n.action,
                       n.read_status AS readStatus,
                       n.created_at AS createdTime
                FROM work_order_mobile_notification n
                INNER JOIN work_order w ON w.id = n.work_order_id AND w.deleted = 0
                WHERE n.recipient_user_id = ?
                  AND n.read_status = 'UNREAD'
                  AND n.deleted = 0
                ORDER BY n.created_at DESC, n.id DESC
                LIMIT 20
                """,
                staffUserId
        );
    }

    private void ensureCanOperateAssignedWorkOrder(AuthPrincipal principal, Long id) {
        if (canManageAllWorkOrders(principal)) {
            return;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order WHERE id = ? AND assignee_id = ? AND deleted = 0",
                Long.class,
                id,
                principal.userId()
        );
        if (count == null || count == 0) {
            throw BusinessException.forbidden("只能处理指派给自己的工单");
        }
    }

    private void ensureMobileAssignedWorkOrder(Long staffUserId, Long id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order WHERE id = ? AND assignee_id = ? AND deleted = 0",
                Long.class,
                id,
                staffUserId
        );
        if (count == null || count == 0) {
            throw BusinessException.forbidden("只能处理指派给自己的工单");
        }
    }

    private boolean canManageAllWorkOrders(AuthPrincipal principal) {
        if (principal != null && principal.roles() != null && principal.roles().contains("SUPER_ADMIN")) {
            return true;
        }
        return hasPermission(principal, "workorder:assign");
    }

    private void ensureCanViewWorkOrders(AuthPrincipal principal) {
        if (principal != null
                && principal.roles() != null
                && principal.roles().contains("SUPER_ADMIN")) {
            return;
        }
        if (!hasPermission(principal, "workorder:view")) {
            throw BusinessException.forbidden("缺少工单查看权限");
        }
    }

    private boolean hasPermission(AuthPrincipal principal, String required) {
        if (principal == null || principal.permissions() == null) {
            return false;
        }
        int colonIndex = required.indexOf(':');
        String moduleWildcard = colonIndex > 0 ? required.substring(0, colonIndex) + ":*" : "";
        return principal.permissions().contains(required)
                || (!moduleWildcard.isEmpty() && principal.permissions().contains(moduleWildcard))
                || principal.permissions().contains("*");
    }

    private Long actorId(AuthPrincipal principal) {
        return principal == null || principal.userId() == null ? 0L : principal.userId();
    }

    private void normalizeMobileWorkOrder(Map<String, Object> row) {
        String remark = row.get("remark") == null ? "" : String.valueOf(row.get("remark"));
        String[] parts = remark.split("\\R", 2);
        String location = trimmed(row.get("location"));
        String description = trimmed(row.get("description"));
        row.put("location", !location.isBlank() ? location : (parts.length > 0 && !parts[0].isBlank() ? parts[0] : "-"));
        row.put("description", !description.isBlank() ? description : (parts.length > 1 ? parts[1] : remark));
        Object imageUrlText = row.remove("imageUrlText");
        if (imageUrlText == null || String.valueOf(imageUrlText).isBlank()) {
            row.put("imageUrls", List.of());
        } else {
            row.put("imageUrls", List.of(String.valueOf(imageUrlText).split(",")));
        }
    }

    private Map<String, String> resolveReporter(AuthPrincipal principal, Long tenantId, Map<String, Object> body) {
        String reporterName = trimmed(body.get("reporterName"));
        String reporterPhone = "";
        String companyName = trimmed(body.get("companyName"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT COALESCE(tc.name, t.contact_person, mu.nickname) AS reporterName,
                       COALESCE(tc.phone, t.contact_phone, mu.phone) AS reporterPhone,
                       t.name AS companyName
                FROM mobile_user mu
                LEFT JOIN tenant t ON t.id = ? AND t.deleted = 0
                LEFT JOIN mobile_user_bind mub ON mub.id = (
                    SELECT b.id
                    FROM mobile_user_bind b
                    WHERE b.mobile_user_id = mu.id
                      AND b.bind_type = 'TENANT'
                      AND b.status = 'ENABLED'
                      AND b.deleted = 0
                    ORDER BY b.id DESC
                    LIMIT 1
                )
                LEFT JOIN tenant_contact tc ON tc.id = mub.tenant_contact_id AND tc.deleted = 0
                WHERE mu.id = ? AND mu.deleted = 0
                LIMIT 1
                """,
                tenantId,
                principal.userId()
        );
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.getFirst();
            if (reporterName.isBlank()) {
                reporterName = trimmed(row.get("reporterName"));
            }
            if (reporterPhone.isBlank()) {
                reporterPhone = trimmed(row.get("reporterPhone"));
            }
            if (companyName.isBlank()) {
                companyName = trimmed(row.get("companyName"));
            }
        }
        if (reporterName.isBlank() || reporterPhone.isBlank()) {
            throw BusinessException.unauthorized("当前租户联系人资料已失效或缺少手机号");
        }
        if (companyName.isBlank()) {
            throw new BusinessException(400, "公司名称不能为空");
        }
        return Map.of(
                "name", truncate(reporterName, 64),
                "phone", truncate(reporterPhone, 32),
                "companyName", truncate(companyName, 128)
        );
    }

    private java.sql.Timestamp reportedAt(Map<String, Object> body) {
        String value = JdbcMaps.str(body, "reportedAt");
        if (value == null) {
            return java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().withNano(0));
        }
        try {
            return java.sql.Timestamp.valueOf(value.replace('T', ' '));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "报修时间格式不正确");
        }
    }

    private String mobileWorkOrderTitle(String problemCategory, String description) {
        String categoryLabel = MOBILE_CATEGORY_LABELS.getOrDefault(
                String.valueOf(problemCategory).trim().toUpperCase(),
                "其他"
        );
        String summary = truncate(String.valueOf(description).replaceAll("\\s+", " ").trim(), 48);
        return truncate(categoryLabel + "报修：" + summary, 128);
    }

    private void copyAlias(Map<String, Object> body, String canonical, String... aliases) {
        if (body.containsKey(canonical)) return;
        for (String alias : aliases) {
            if (body.containsKey(alias)) {
                body.put(canonical, body.get(alias));
                return;
            }
        }
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

    private void syncMobileWorkOrderAttachments(
            AuthPrincipal principal,
            Long workOrderId,
            Map<String, Object> body
    ) {
        if (!body.containsKey("images") && !body.containsKey("attachments")) return;
        if (mobileStaffFileService == null) throw new BusinessException(503, "内部附件服务暂不可用");
        List<Long> imageIds = fileIds(body.get("images"));
        List<Long> attachmentIds = fileIds(body.get("attachments"));
        List<Long> allIds = java.util.stream.Stream.concat(imageIds.stream(), attachmentIds.stream()).distinct().toList();
        if (!allIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(allIds.size(), "?"));
            List<Long> draftIds = jdbcTemplate.queryForList(
                    "SELECT id FROM file_upload WHERE id IN (" + placeholders + ") AND biz_id IS NULL AND biz_type IN ('MOBILE_DRAFT','STAFF_DRAFT')",
                    Long.class,
                    allIds.toArray()
            );
            mobileStaffFileService.claimDraftFiles(principal, draftIds, "WORK_ORDER", workOrderId, "workorders");
            Long valid = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM file_upload WHERE id IN (" + placeholders + ") AND biz_type = 'WORK_ORDER' AND biz_id = ? AND access_scope = 'INTERNAL' AND deleted = 0",
                    Long.class,
                    concatObjects(allIds, workOrderId)
            );
            if (valid == null || valid != allIds.size()) throw new BusinessException(400, "工单图片或附件无效");
        }
        replaceWorkOrderAttachments(workOrderId, imageIds, attachmentIds, actorId(principal));
    }

    private void syncAdminWorkOrderAttachments(AuthPrincipal principal, Long workOrderId, Map<String, Object> body) {
        if (!body.containsKey("images") && !body.containsKey("attachments")) return;
        List<Long> imageIds = fileIds(body.get("images"));
        List<Long> attachmentIds = fileIds(body.get("attachments"));
        List<Long> allIds = java.util.stream.Stream.concat(imageIds.stream(), attachmentIds.stream()).distinct().toList();
        Long actor = actorId(principal);
        if (!allIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(allIds.size(), "?"));
            Long valid = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM file_upload
                    WHERE id IN (%s) AND access_scope = 'INTERNAL' AND deleted = 0
                      AND (
                        (biz_type IN ('CUSTOM_FIELD_IMAGE','CUSTOM_FIELD_ATTACHMENT')
                         AND biz_id IS NULL AND workspace_key = 'workorders' AND owner_sys_user_id = ?)
                        OR (biz_type = 'WORK_ORDER' AND biz_id = ?)
                      )
                    """.formatted(placeholders),
                    Long.class,
                    concatObjects(allIds, actor, workOrderId)
            );
            if (valid == null || valid != allIds.size()) throw new BusinessException(400, "工单图片或附件无效");
            jdbcTemplate.update(
                    "UPDATE file_upload SET biz_type = 'WORK_ORDER', biz_id = ?, workspace_key = 'workorders', updated_by = ? WHERE id IN (" + placeholders + ") AND biz_id IS NULL AND owner_sys_user_id = ? AND deleted = 0",
                    concatObjects(List.of(workOrderId, actor), allIds, actor)
            );
        }
        replaceWorkOrderAttachments(workOrderId, imageIds, attachmentIds, actor);
    }

    private void replaceWorkOrderAttachments(
            Long workOrderId,
            List<Long> imageIds,
            List<Long> attachmentIds,
            Long actor
    ) {
        jdbcTemplate.update(
                "UPDATE work_order_attachment SET deleted = 1, status = 'DISABLED', updated_by = ? WHERE work_order_id = ? AND work_order_record_id IS NULL AND deleted = 0",
                actor,
                workOrderId
        );
        insertWorkOrderAttachments(workOrderId, imageIds, "IMAGE", actor);
        insertWorkOrderAttachments(workOrderId, attachmentIds, "ATTACHMENT", actor);
    }

    private void insertWorkOrderAttachments(Long workOrderId, List<Long> fileIds, String type, Long actor) {
        for (Long fileId : fileIds) {
            jdbcTemplate.update(
                    "INSERT INTO work_order_attachment (work_order_id, file_id, attachment_type, status, created_by, updated_by) VALUES (?, ?, ?, 'ENABLED', ?, ?)",
                    workOrderId,
                    fileId,
                    type,
                    actor,
                    actor
            );
        }
    }

    private Map<String, Object> workOrderFileValue(Long workOrderId, String type) {
        List<Map<String, Object>> files = jdbcTemplate.queryForList(
                """
                SELECT f.id AS fileId, f.original_name AS name, f.file_size AS size,
                       CONCAT('/api/files/', f.id) AS url, a.id AS sortOrder
                FROM work_order_attachment a
                JOIN file_upload f ON f.id = a.file_id AND f.deleted = 0
                WHERE a.work_order_id = ? AND a.work_order_record_id IS NULL
                  AND a.attachment_type = ? AND a.deleted = 0
                ORDER BY a.id
                """,
                workOrderId,
                type
        );
        return Map.of("version", 1, "files", files);
    }

    private List<Map<String, Object>> workOrderHandlingRecords(Long workOrderId) {
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                """
                SELECT record.id,
                       record.action,
                       record.content,
                       record.operator_id AS operatorId,
                       COALESCE(NULLIF(user_record.real_name, ''), user_record.username, '系统') AS operatorName,
                       record.created_at AS createdTime
                FROM work_order_record record
                LEFT JOIN sys_user user_record
                  ON user_record.id = record.operator_id AND user_record.deleted = 0
                WHERE record.work_order_id = ? AND record.deleted = 0
                ORDER BY record.created_at, record.id
                """,
                workOrderId
        );
        records.forEach(record -> record.put("attachments", jdbcTemplate.queryForList(
                """
                SELECT file_record.id AS fileId,
                       file_record.original_name AS name,
                       file_record.file_size AS size,
                       attachment.attachment_type AS attachmentType,
                       CONCAT('/api/files/', file_record.id) AS url,
                       attachment.id AS sortOrder
                FROM work_order_attachment attachment
                JOIN file_upload file_record
                  ON file_record.id = attachment.file_id AND file_record.deleted = 0
                WHERE attachment.work_order_record_id = ?
                  AND attachment.deleted = 0
                ORDER BY attachment.id
                """,
                record.get("id")
        )));
        return records;
    }

    private List<String> propertyCustomFieldKeys(Long workOrderId) {
        return jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT value_record.field_key AS fieldKey
                        FROM custom_field_value value_record
                        WHERE value_record.workspace_key = 'workorders'
                          AND value_record.record_id = ?
                          AND value_record.deleted = 0
                          AND EXISTS (
                            SELECT 1
                            FROM work_order_record handling_record
                            WHERE handling_record.work_order_id = value_record.record_id
                              AND handling_record.operator_id = value_record.updated_by
                              AND handling_record.action IN ('PROGRESS', 'COMPLETED', 'COMPLETE')
                              AND ABS(TIMESTAMPDIFF(SECOND, handling_record.created_at, value_record.updated_at)) <= 5
                              AND handling_record.deleted = 0
                          )
                        ORDER BY value_record.field_key
                        """,
                        workOrderId
                ).stream()
                .map(row -> String.valueOf(row.get("fieldKey")))
                .toList();
    }

    private Object[] concatObjects(List<?> first, Object... rest) {
        List<Object> values = new java.util.ArrayList<>(first);
        values.addAll(List.of(rest));
        return values.toArray();
    }

    private Object[] concatObjects(List<?> first, List<?> middle, Object... rest) {
        List<Object> values = new java.util.ArrayList<>(first);
        values.addAll(middle);
        values.addAll(List.of(rest));
        return values.toArray();
    }

    private void collectFileIds(Object value, List<Long> result) {
        if (value instanceof Map<?, ?> map) {
            Object fileId = map.get("fileId");
            if (fileId != null) {
                try {
                    long id = Long.parseLong(String.valueOf(fileId));
                    if (id > 0) result.add(id);
                } catch (NumberFormatException ignored) {
                    // Legacy URL-only files remain readable but cannot be claimed.
                }
            }
            map.values().forEach(item -> collectFileIds(item, result));
        } else if (value instanceof Iterable<?> items) {
            items.forEach(item -> collectFileIds(item, result));
        }
    }

    private String currentMobileWorkOrderStatus(Long staffUserId, Long id) {
        List<String> statuses = jdbcTemplate.queryForList(
                """
                SELECT work_status
                FROM work_order
                WHERE id = ? AND assignee_id = ? AND deleted = 0
                """,
                String.class,
                id,
                staffUserId
        );
        if (statuses.isEmpty()) {
            throw BusinessException.forbidden("只能处理指派给自己的工单");
        }
        return statuses.getFirst();
    }

    private Map<String, String> resolveInternalReporter(AuthPrincipal principal) {
        String reporterName = "";
        String reporterPhone = "";
        if (principal != null && principal.userId() != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT COALESCE(NULLIF(real_name, ''), username) AS reporterName,
                           phone AS reporterPhone
                    FROM sys_user
                    WHERE id = ? AND deleted = 0
                    LIMIT 1
                    """,
                    principal.userId()
            );
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.getFirst();
                reporterName = trimmed(row.get("reporterName"));
                reporterPhone = trimmed(row.get("reporterPhone"));
            }
            if (reporterName.isBlank()) {
                reporterName = trimmed(principal.username());
            }
        }
        return Map.of("name", truncate(reporterName, 64), "phone", truncate(reporterPhone, 32));
    }

    private String trimmed(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index);
    }

    private void append(StringBuilder sql, StringBuilder countSql, String clause) {
        sql.append(clause);
        countSql.append(clause);
    }

    private Long parseLongOrMinusOne(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return -1L;
        }
    }
}

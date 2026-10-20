package com.dehui.property.modules.inspection.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldApplicationService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.inspection.application.InspectionInspectorValueResolver;
import com.dehui.property.modules.inspection.application.InspectionTypeValueResolver;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionController {

    private static final Map<String, String> ORDER_FIELDS = Map.ofEntries(
            Map.entry("inspectionDate", "i.inspection_date"),
            Map.entry("inspector", "i.inspector"),
            Map.entry("inspectionType", "i.inspection_type"),
            Map.entry("area", "i.area"),
            Map.entry("target", "i.target"),
            Map.entry("result", "i.result"),
            Map.entry("status", "i.inspection_status"),
            Map.entry("createdAt", "i.created_at"),
            Map.entry("updatedAt", "i.updated_at")
    );

    private static final int MAX_ATTACHMENTS = 30;
    private static final int MAX_TYPE_CODE_LENGTH = 32;
    private static final String ACTIVE_TYPE_STATUS = "ENABLED";
    private static final String DISABLED_TYPE_STATUS = "DISABLED";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp"
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;
    private CustomFieldApplicationService customFieldApplicationService;

    public InspectionController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    @Autowired
    public InspectionController(
            JdbcTemplate jdbcTemplate,
            CustomFieldQueryService customFieldQueryService,
            CustomFieldProjectionService customFieldProjectionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldQueryService = customFieldQueryService;
        this.customFieldProjectionService = customFieldProjectionService;
    }

    @Autowired(required = false)
    void setCustomFieldApplicationService(CustomFieldApplicationService service) {
        this.customFieldApplicationService = service;
    }

    @GetMapping("/inspections")
    public ApiResponse<PageResponse<Map<String, Object>>> inspections(
            @RequestParam(required = false) String inspector,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String inspectionType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id,
                       i.code,
                       i.inspection_date AS inspectionDate,
                       i.inspector,
                       i.inspection_type AS inspectionType,
                       i.area,
                       i.target,
                       i.result,
                       i.problem_description AS problemDescription,
                       i.action_taken AS actionTaken,
                       i.inspection_status AS status,
                       i.remark,
                       i.created_at AS createdAt,
                       i.updated_at AS updatedAt
                FROM inspection_record i
                WHERE i.deleted = 0
                """);
        StringBuilder countSql = new StringBuilder("""
                SELECT COUNT(*)
                FROM inspection_record i
                WHERE i.deleted = 0
                """);
        ArrayList<Object> args = appendFilters(
                sql,
                countSql,
                inspector,
                target,
                inspectionType,
                result,
                status,
                startDate,
                endDate
        );

        CompiledQuery customQuery = compileCustomQuery(customFilters, groupBy, sorts, sortField, sortDirection);
        if (customQuery != null && customQuery.hasPredicates()) {
            sql.append(" AND ").append(customQuery.predicateSql());
            countSql.append(" AND ").append(customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(
                ORDER_FIELDS,
                groupBy,
                sorts,
                sortField,
                sortDirection,
                "i.inspection_date",
                "i.id"
        )
                : String.join(", ", customQuery.orderExpressions());
        sql.append(" ORDER BY ").append(orderBy);
        PageResponse<Map<String, Object>> pageResult = JdbcPagination.query(
                jdbcTemplate,
                sql.toString(),
                countSql.toString(),
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            pageResult = customFieldProjectionService.attach("inspections", pageResult);
        }
        return ApiResponse.success(pageResult);
    }

    @GetMapping("/inspections/{id}")
    public ApiResponse<Map<String, Object>> inspection(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT i.id,
                       i.code,
                       i.inspection_date AS inspectionDate,
                       i.inspector,
                       i.inspection_type AS inspectionType,
                       i.area,
                       i.target,
                       i.result,
                       i.problem_description AS problemDescription,
                       i.action_taken AS actionTaken,
                       i.inspection_status AS status,
                       i.remark,
                       i.created_at AS createdAt,
                       i.updated_at AS updatedAt
                FROM inspection_record i
                WHERE i.deleted = 0 AND i.id = ?
                """,
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Inspection record not found");
        }
        Map<String, Object> record = new LinkedHashMap<>(rows.get(0));
        if (customFieldProjectionService != null) {
            record = new LinkedHashMap<>(
                    customFieldProjectionService.attach("inspections", List.of(record)).getFirst()
            );
        }
        List<Map<String, Object>> attachments = listInspectionAttachments(id);
        record.put("attachments", attachments);
        record.put("images", attachments);
        List<Map<String, Object>> history = listInspectionHistory(id);
        Map<Long, List<Map<String, Object>>> resolutionImages = listInspectionHistoryAttachments(id);
        history.forEach(item -> item.put(
                "resolutionImages",
                resolutionImages.getOrDefault(JdbcMaps.longVal(item, "id"), List.of())
        ));
        record.put("history", history);
        return ApiResponse.success(record);
    }

    @GetMapping("/inspection-types")
    public ApiResponse<List<Map<String, Object>>> inspectionTypes(
            @RequestParam(required = false) Boolean includeDisabled
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,
                       code,
                       name,
                       description,
                       status,
                       sort_order AS sortOrder,
                       created_at AS createdAt,
                       updated_at AS updatedAt,
                       created_by AS createdBy,
                       updated_by AS updatedBy,
                       deleted
                FROM inspection_type
                WHERE deleted = 0
                """);
        if (!Boolean.TRUE.equals(includeDisabled)) {
            sql.append(" AND status = ? ");
            sql.append(" AND CHAR_LENGTH(TRIM(code)) <= 32 ");
            sql.append("ORDER BY sort_order ASC, id ASC");
            return ApiResponse.success(jdbcTemplate.queryForList(sql.toString(), ACTIVE_TYPE_STATUS));
        }
        sql.append(" ORDER BY sort_order ASC, id ASC");
        return ApiResponse.success(jdbcTemplate.queryForList(sql.toString()));
    }

    @PostMapping("/inspection-types")
    @Transactional
    public ApiResponse<Map<String, Object>> createInspectionType(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        String code = normalizeText(JdbcMaps.requiredStr(body, "Inspection type code is required", "code"));
        String name = normalizeText(JdbcMaps.requiredStr(body, "Inspection type name is required", "name"));
        String description = normalizeText(JdbcMaps.str(body, "description"));
        String status = normalizeTypeStatus(JdbcMaps.str(body, "status"));
        Integer sortOrder = IntegerValue(body, "sortOrder", "sort_order");
        Long actorId = actorId(principal);
        if (code == null || code.isBlank()) {
            throw new BusinessException(400, "Inspection type code is required");
        }
        requireStorableTypeCode(code);
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "Inspection type name is required");
        }
        if (!queryInspectionTypeByCode(code, true).isEmpty()) {
            throw new BusinessException(400, "Inspection type code already exists");
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO inspection_type (code, name, description, status, sort_order, created_by, updated_by, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    code,
                    name,
                    description,
                    status,
                    sortOrder,
                    actorId,
                    actorId
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "Inspection type code already exists");
        }
        return ApiResponse.success(queryInspectionTypeByCode(code, true));
    }

    @PutMapping("/inspection-types/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> updateInspectionType(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        Map<String, Object> existing = queryInspectionTypeById(id, true);
        if (existing.isEmpty()) {
            throw new BusinessException(404, "Inspection type not found");
        }
        String requestedCode = normalizeText(JdbcMaps.str(body, "code"));
        String code = asString(existing.get("code"));
        if (requestedCode != null && !requestedCode.equalsIgnoreCase(code)) {
            throw new BusinessException(400, "Inspection type code is immutable");
        }
        String name = normalizeText(JdbcMaps.requiredStr(body, "Inspection type name is required", "name"));
        String description = normalizeText(JdbcMaps.str(body, "description"));
        String status = normalizeTypeStatus(JdbcMaps.str(body, "status"));
        if (ACTIVE_TYPE_STATUS.equals(status)) {
            requireStorableTypeCode(code);
        }
        Integer sortOrder = IntegerValue(body, "sortOrder", "sort_order");
        Long actorId = actorId(principal);

        int updated = jdbcTemplate.update(
                """
                UPDATE inspection_type
                SET name = ?,
                    description = ?,
                    status = ?,
                    sort_order = ?,
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                name,
                description,
                status,
                sortOrder,
                actorId,
                id
        );
        if (updated == 0) {
            throw new BusinessException(404, "Inspection type not found");
        }
        return ApiResponse.success(queryInspectionTypeById(id, true));
    }

    @PostMapping("/inspection-types/{id}/enable")
    @Transactional
    public ApiResponse<Void> enableInspectionType(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        Map<String, Object> existing = queryInspectionTypeById(id, true);
        if (existing.isEmpty()) {
            throw new BusinessException(404, "Inspection type not found");
        }
        requireStorableTypeCode(asString(existing.get("code")));
        int updated = jdbcTemplate.update(
                "UPDATE inspection_type SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                ACTIVE_TYPE_STATUS,
                actorId(principal),
                id
        );
        if (updated == 0) {
            throw new BusinessException(404, "Inspection type not found");
        }
        return ApiResponse.success();
    }

    @PostMapping("/inspection-types/{id}/disable")
    @Transactional
    public ApiResponse<Void> disableInspectionType(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        int updated = jdbcTemplate.update(
                "UPDATE inspection_type SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                DISABLED_TYPE_STATUS,
                actorId(principal),
                id
        );
        if (updated == 0) {
            throw new BusinessException(404, "Inspection type not found");
        }
        return ApiResponse.success();
    }

    @DeleteMapping("/inspection-types/{id}")
    @Transactional
    public ApiResponse<Void> deleteInspectionType(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        Map<String, Object> type = queryInspectionTypeById(id, true);
        if (type.isEmpty()) {
            throw new BusinessException(404, "Inspection type not found");
        }
        String code = asString(type.get("code"));
        Long used = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inspection_record
                WHERE deleted = 0
                  AND UPPER(TRIM(inspection_type)) = UPPER(TRIM(?))
                """,
                Long.class,
                code
        );
        if (used != null && used > 0) {
            throw new BusinessException(409, "Inspection type has been used by inspection record and cannot be deleted");
        }
        int deleted = jdbcTemplate.update(
                "UPDATE inspection_type SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                actorId(principal),
                id
        );
        if (deleted == 0) {
            throw new BusinessException(404, "Inspection type not found");
        }
        return ApiResponse.success();
    }

    @PostMapping("/inspections")
    @Transactional
    public ApiResponse<Map<String, Object>> create(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        Long actorId = actorId(principal);
        String inspector = InspectionInspectorValueResolver.resolve(
                JdbcMaps.str(body, "inspector")
        );
        String inspectionType = resolveTypeCodeForCreate(body);
        String result = JdbcMaps.strOr(body, "NORMAL", "result");
        String problemDescription = JdbcMaps.str(body, "problemDescription");
        if ("ABNORMAL".equalsIgnoreCase(result) && (problemDescription == null || problemDescription.isBlank())) {
            throw new BusinessException(400, "Abnormal inspection requires problem description");
        }
        final String sql = """
                INSERT INTO inspection_record (
                    code, inspection_date, inspector, inspection_type, area, target, result,
                    problem_description, action_taken, inspection_status, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, JdbcMaps.code("INSP"));
            statement.setString(2, JdbcMaps.requiredStr(body, "Inspection date is required", "inspectionDate"));
            statement.setString(3, inspector);
            statement.setString(4, inspectionType);
            statement.setString(5, JdbcMaps.requiredStr(body, "Inspection area is required", "area"));
            statement.setString(6, JdbcMaps.requiredStr(body, "Inspection target is required", "target"));
            statement.setString(7, result);
            statement.setString(8, problemDescription);
            statement.setString(9, JdbcMaps.str(body, "actionTaken"));
            statement.setString(10, "OPEN");
            statement.setLong(11, actorId);
            statement.setLong(12, actorId);
            statement.setString(13, JdbcMaps.str(body, "remark"));
            return statement;
        }, keyHolder);

        Long createdId = generatedId(keyHolder);
        if (createdId == null) {
            throw new BusinessException(500, "Inspection record create failed");
        }

        List<RequestedAttachment> requestedAttachments = resolveRequestedAttachments(body);
        if (requestedAttachments != null) {
            syncInspectionAttachments(createdId, requestedAttachments, actorId);
        }
        if (customFieldApplicationService != null) {
            customFieldApplicationService.saveValues(
                    "inspections", createdId, stringMap(body.get("customFields")), principal
            );
        }
        return ApiResponse.success(Map.of("id", createdId));
    }

    @PutMapping("/inspections/{id}")
    @Transactional
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        Map<String, Object> current = queryInspection(id);
        if (current.isEmpty()) {
            throw new BusinessException(404, "Inspection record not found");
        }

        Long actorId = actorId(principal);
        String inspector = InspectionInspectorValueResolver.resolve(
                JdbcMaps.str(body, "inspector")
        );
        String currentType = asString(current.get("inspection_type"));
        String inspectionType = resolveTypeCodeForUpdate(body, currentType);

        int updated = jdbcTemplate.update(
                """
                UPDATE inspection_record SET
                    inspection_date = ?,
                    inspector = ?,
                    inspection_type = ?,
                    area = ?,
                    target = ?,
                    result = ?,
                    problem_description = ?,
                    action_taken = ?,
                    inspection_status = ?,
                    updated_by = ?,
                    remark = ?
                WHERE id = ? AND deleted = 0
                """,
                JdbcMaps.requiredStr(body, "Inspection date is required", "inspectionDate"),
                inspector,
                inspectionType,
                JdbcMaps.requiredStr(body, "Inspection area is required", "area"),
                JdbcMaps.requiredStr(body, "Inspection target is required", "target"),
                JdbcMaps.strOr(body, "NORMAL", "result"),
                JdbcMaps.str(body, "problemDescription"),
                JdbcMaps.str(body, "actionTaken"),
                asString(current.get("inspection_status")),
                actorId,
                JdbcMaps.str(body, "remark"),
                id
        );
        if (updated == 0) {
            throw new BusinessException(404, "Inspection record not found");
        }

        List<RequestedAttachment> requestedAttachments = resolveRequestedAttachments(body);
        if (requestedAttachments != null) {
            syncInspectionAttachments(id, requestedAttachments, actorId);
        }
        if (customFieldApplicationService != null && body.containsKey("customFields")) {
            customFieldApplicationService.saveValues(
                    "inspections", id, stringMap(body.get("customFields")), principal
            );
        }

        return ApiResponse.success();
    }

    @DeleteMapping("/inspections/{id}/attachments/{attachmentId}")
    @Transactional
    public ApiResponse<Void> deleteAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT id FROM inspection_record WHERE id = ? AND deleted = 0",
                id
        );
        if (records.isEmpty()) {
            throw new BusinessException(404, "Inspection record not found");
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE inspection_attachment
                SET deleted = 1, updated_by = ?
                WHERE id = ? AND inspection_id = ? AND deleted = 0
                """,
                actorId(principal),
                attachmentId,
                id
        );
        if (updated == 0) {
            throw new BusinessException(404, "Attachment not found");
        }
        return ApiResponse.success();
    }

    @DeleteMapping("/inspections/{id}")
    @Transactional
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        int updated = jdbcTemplate.update(
                "UPDATE inspection_record SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                actorId(principal),
                id
        );
        if (updated == 0) {
            throw new BusinessException(404, "Inspection record not found");
        }
        jdbcTemplate.update(
                "UPDATE inspection_attachment SET deleted = 1, updated_by = ? WHERE inspection_id = ? AND deleted = 0",
                actorId(principal),
                id
        );
        return ApiResponse.success();
    }

    @PostMapping("/inspections/{id}/close")
    public ApiResponse<Void> close(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        int updated = jdbcTemplate.update(
                "UPDATE inspection_record SET inspection_status = 'CLOSED', updated_by = ? WHERE id = ? AND deleted = 0",
                actorId(principal),
                id
        );
        if (updated == 0) {
            throw new BusinessException(404, "Inspection record not found");
        }
        return ApiResponse.success();
    }

    @GetMapping("/inspections/summary")
    public ApiResponse<Map<String, Object>> summary(
            @RequestParam(required = false) String inspector,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String inspectionType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String customFilters
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS totalCount,
                       SUM(CASE WHEN i.result = 'NORMAL' THEN 1 ELSE 0 END) AS normalCount,
                       SUM(CASE WHEN i.result = 'ABNORMAL' THEN 1 ELSE 0 END) AS abnormalCount,
                       SUM(CASE WHEN i.inspection_status = 'OPEN' THEN 1 ELSE 0 END) AS openCount,
                       SUM(CASE WHEN i.inspection_status = 'CLOSED' THEN 1 ELSE 0 END) AS closedCount
                FROM inspection_record i
                WHERE i.deleted = 0
                """);
        StringBuilder ignoredCountSql = new StringBuilder();
        ArrayList<Object> args = appendFilters(
                sql,
                ignoredCountSql,
                inspector,
                target,
                inspectionType,
                result,
                status,
                startDate,
                endDate
        );
        CompiledQuery customQuery = compileCustomQuery(customFilters, null, null, null, null);
        if (customQuery != null && customQuery.hasPredicates()) {
            sql.append(" AND ").append(customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }
        Map<String, Object> values = jdbcTemplate.queryForMap(sql.toString(), args.toArray());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", number(values.get("totalCount")));
        summary.put("normalCount", number(values.get("normalCount")));
        summary.put("abnormalCount", number(values.get("abnormalCount")));
        summary.put("openCount", number(values.get("openCount")));
        summary.put("closedCount", number(values.get("closedCount")));
        return ApiResponse.success(summary);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> inspections(
            String inspector,
            String target,
            String inspectionType,
            String result,
            String status,
            String startDate,
            String endDate,
            String sortField,
            String sortDirection,
            String groupBy,
            String sorts,
            Integer page,
            Integer pageSize
    ) {
        return inspections(
                inspector, target, inspectionType, result, status, startDate, endDate,
                null, sortField, sortDirection, groupBy, sorts, page, pageSize
        );
    }

    public ApiResponse<Map<String, Object>> summary(
            String inspector,
            String target,
            String inspectionType,
            String result,
            String status,
            String startDate,
            String endDate
    ) {
        return summary(inspector, target, inspectionType, result, status, startDate, endDate, null);
    }

    private CompiledQuery compileCustomQuery(
            String customFilters,
            String groupBy,
            String sorts,
            String sortField,
            String sortDirection
    ) {
        if (customFieldQueryService == null) return null;
        NativeOrderPolicy orderPolicy = groupBy == null && sorts == null
                ? null
                : NativeOrderPolicy.of(
                ORDER_FIELDS,
                sortField,
                sortDirection,
                "i.inspection_date",
                "i.id"
        );
        return customFieldQueryService.compile(
                "inspections",
                customFilters,
                groupBy,
                sorts,
                orderPolicy
        );
    }

    private Map<String, Object> queryInspection(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM inspection_record WHERE id = ? AND deleted = 0",
                id
        );
        if (rows.isEmpty()) return Map.of();
        return rows.get(0);
    }

    private Map<String, Object> queryInspectionTypeById(Long id, boolean includeDeleted) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       code,
                       name,
                       description,
                       status,
                       sort_order AS sortOrder,
                       created_at AS createdAt,
                       updated_at AS updatedAt,
                       created_by AS createdBy,
                       updated_by AS updatedBy,
                       deleted
                FROM inspection_type
                WHERE id = ? %s
                """.formatted(includeDeleted ? "" : "AND deleted = 0"),
                id
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> queryInspectionTypeByCode(String code, boolean includeDisabled) {
        if (code == null || code.isBlank()) return Map.of();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       code,
                       name,
                       description,
                       status,
                       sort_order AS sortOrder,
                       created_at AS createdAt,
                       updated_at AS updatedAt,
                       created_by AS createdBy,
                       updated_by AS updatedBy,
                       deleted
                FROM inspection_type
                WHERE deleted = 0
                  AND UPPER(TRIM(code)) = UPPER(TRIM(?))
                  %s
                """.formatted(includeDisabled ? "" : "AND status = '" + ACTIVE_TYPE_STATUS + "'"),
                code
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> queryInspectionTypeByName(String name, boolean includeDisabled) {
        if (name == null || name.isBlank()) return Map.of();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       code,
                       name,
                       description,
                       status,
                       sort_order AS sortOrder,
                       created_at AS createdAt,
                       updated_at AS updatedAt,
                       created_by AS createdBy,
                       updated_by AS updatedBy,
                       deleted
                FROM inspection_type
                WHERE deleted = 0
                  AND UPPER(TRIM(name)) = UPPER(TRIM(?))
                  %s
                """.formatted(includeDisabled ? "" : "AND status = '" + ACTIVE_TYPE_STATUS + "'"),
                name
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private String resolveTypeCodeForCreate(Map<String, Object> body) {
        String raw = normalizeText(JdbcMaps.requiredStr(body, "Inspection type is required", "inspectionType", "type"));
        return resolveTypeCode(raw, null);
    }

    private String resolveTypeCodeForUpdate(Map<String, Object> body, String currentType) {
        String raw = normalizeText(JdbcMaps.str(body, "inspectionType", "type"));
        if (raw == null || raw.isBlank()) {
            return currentType == null ? "" : currentType;
        }
        return resolveTypeCode(raw, currentType);
    }

    private String resolveTypeCode(String raw, String currentType) {
        return InspectionTypeValueResolver.resolve(jdbcTemplate, raw, currentType);
    }

    private void requireStorableTypeCode(String code) {
        if (code == null || code.trim().length() > MAX_TYPE_CODE_LENGTH) {
            throw new BusinessException(400, "巡检类型编码不能超过32个字符");
        }
    }

    private String normalizeTypeStatus(Object raw) {
        String value = normalizeText(raw);
        if (value == null || value.isBlank()) return ACTIVE_TYPE_STATUS;
        if (ACTIVE_TYPE_STATUS.equalsIgnoreCase(value)) return ACTIVE_TYPE_STATUS;
        if (DISABLED_TYPE_STATUS.equalsIgnoreCase(value)) return DISABLED_TYPE_STATUS;
        throw new BusinessException(400, "Invalid inspection type status");
    }

    private int IntegerValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source == null ? null : source.get(key);
            if (value == null) continue;
            if (value instanceof Number number) return number.intValue();
            String text = String.valueOf(value).trim();
            if (text.isBlank()) continue;
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                throw new BusinessException(400, "Invalid sort order");
            }
        }
        return 0;
    }

    private List<RequestedAttachment> resolveRequestedAttachments(Map<String, Object> body) {
        if (body == null) return null;
        if (body.containsKey("images")) {
            Object images = body.get("images");
            if (images instanceof Map<?, ?> map && map.containsKey("files")) {
                return parseRequestedAttachments(map.get("files"));
            }
            return parseRequestedAttachments(images);
        }
        if (body.containsKey("attachments")) {
            return parseRequestedAttachments(body.get("attachments"));
        }
        if (body.containsKey("attachmentFileIds")) {
            return parseRequestedAttachmentFileIds(body.get("attachmentFileIds"));
        }
        return null;
    }

    private Map<String, Object> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<RequestedAttachment> parseRequestedAttachmentFileIds(Object rawIds) {
        List<Object> source = normalizeList(rawIds);
        if (source == null) return new ArrayList<>();
        List<RequestedAttachment> attachments = new ArrayList<>();
        int order = 1;
        for (Object value : source) {
            Long fileId = parseLong(value);
            if (fileId == null) continue;
            attachments.add(new RequestedAttachment(fileId, order++));
        }
        return attachments;
    }

    private List<RequestedAttachment> parseRequestedAttachments(Object raw) {
        List<Object> source = normalizeList(raw);
        if (source == null) return new ArrayList<>();
        List<RequestedAttachment> attachments = new ArrayList<>();
        int index = 1;
        for (Object item : source) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<?, ?> typed = map;
            Long fileId = parseLong(
                    typed.get("fileId"),
                    typed.get("file_id"),
                    typed.get("id")
            );
            if (fileId == null) continue;
            Integer sortOrder = parseInt(typed.get("sortOrder"), typed.get("sort_order"));
            attachments.add(new RequestedAttachment(fileId, sortOrder != null ? sortOrder : index));
            index += 1;
        }
        return attachments;
    }

    @SuppressWarnings("unchecked")
    private List<Object> normalizeList(Object raw) {
        if (raw == null) return null;
        if (raw instanceof List<?> list) return (List<Object>) list;
        if (raw instanceof Object[] array) {
            List<Object> result = new ArrayList<>(array.length);
            for (Object item : array) {
                result.add(item);
            }
            return result;
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) return new ArrayList<>();
            String[] parts = trimmed.split("\\s*,\\s*");
            List<Object> result = new ArrayList<>(parts.length);
            for (String part : parts) {
                if (!part.isBlank()) result.add(part.trim());
            }
            return result;
        }
        return List.of(raw);
    }

    private void syncInspectionAttachments(Long inspectionId, List<RequestedAttachment> requestedAttachments, Long actorId) {
        if (requestedAttachments == null) return;
        List<RequestedAttachment> normalized = new ArrayList<>();
        Set<Long> fileIdsSeen = new HashSet<>();
        for (RequestedAttachment attachment : requestedAttachments) {
            if (attachment == null || attachment.fileId() == null) continue;
            if (!fileIdsSeen.add(attachment.fileId())) continue;
            validateImageFile(attachment.fileId());
            normalized.add(attachment);
        }

        if (normalized.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(400, "A inspection can contain at most " + MAX_ATTACHMENTS + " images");
        }

        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(
                "SELECT id, file_id AS fileId, sort_order AS sortOrder, deleted FROM inspection_attachment WHERE inspection_id = ?",
                inspectionId
        );
        Map<Long, Long> activeIdsByFile = new LinkedHashMap<>();
        Map<Long, Long> deletedIdsByFile = new LinkedHashMap<>();
        for (Map<String, Object> row : existingRows) {
            Long fileId = parseLong(row.get("fileId"));
            if (fileId == null) continue;
            Long id = parseLong(row.get("id"));
            Object deletedValue = row.get("deleted");
            boolean deleted = deletedValue instanceof Boolean flag
                    ? flag
                    : Long.valueOf(1L).equals(parseLong(deletedValue));
            if (deleted) {
                if (id != null) {
                    deletedIdsByFile.put(fileId, id);
                }
                continue;
            }
            if (id != null) {
                activeIdsByFile.put(fileId, id);
            }
        }

        Set<Long> requestedFileIds = new LinkedHashSet<>();
        for (int index = 0; index < normalized.size(); index += 1) {
            RequestedAttachment requested = normalized.get(index);
            Long fileId = requested.fileId();
            int sortOrder = requested.sortOrder() != null ? requested.sortOrder() : index + 1;
            requestedFileIds.add(fileId);

            if (activeIdsByFile.containsKey(fileId)) {
                jdbcTemplate.update(
                        "UPDATE inspection_attachment SET sort_order = ?, updated_by = ?, deleted = 0 WHERE inspection_id = ? AND file_id = ? AND deleted = 0",
                        sortOrder,
                        actorId,
                        inspectionId,
                        fileId
                );
                continue;
            }
            if (deletedIdsByFile.containsKey(fileId)) {
                jdbcTemplate.update(
                        """
                        UPDATE inspection_attachment
                        SET deleted = 0, sort_order = ?, updated_by = ?
                        WHERE id = ?
                        """,
                        sortOrder,
                        actorId,
                        deletedIdsByFile.get(fileId)
                );
                continue;
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO inspection_attachment (inspection_id, file_id, sort_order, created_by, updated_by, deleted)
                    VALUES (?, ?, ?, ?, ?, 0)
                    """,
                    inspectionId,
                    fileId,
                    sortOrder,
                    actorId,
                    actorId
            );
        }

        if (requestedFileIds.isEmpty()) {
            jdbcTemplate.update(
                    "UPDATE inspection_attachment SET deleted = 1, updated_by = ? WHERE inspection_id = ? AND deleted = 0",
                    actorId,
                    inspectionId
            );
            return;
        }

        String inClause = String.join(", ", requestedFileIds.stream().map(item -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(actorId);
        args.add(inspectionId);
        args.addAll(requestedFileIds);
        String sql = """
                UPDATE inspection_attachment
                SET deleted = 1, updated_by = ?
                WHERE inspection_id = ? AND deleted = 0 AND file_id NOT IN (%s)
                """.formatted(inClause);
        jdbcTemplate.update(sql, args.toArray());
    }

    private void validateImageFile(Long fileId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT original_name AS originalName, stored_name AS storedName, mime_type AS mimeType
                FROM file_upload
                WHERE id = ? AND deleted = 0
                """,
                fileId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(400, "Attachment file not found");
        }
        Map<String, Object> file = rows.get(0);
        String mimeType = asString(file.get("mimeType"));
        String fileName = asString(file.get("originalName"));
        String storedName = asString(file.get("storedName"));
        String extensionSource = normalizeText(fileName != null ? fileName : storedName);
        if (mimeType != null && IMAGE_MIME_TYPES.contains(mimeType.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (extensionSource != null && IMAGE_EXTENSIONS.contains(fileExtension(extensionSource))) {
            return;
        }
        throw new BusinessException(400, "Only image file can be used as inspection attachment");
    }

    private List<Map<String, Object>> listInspectionAttachments(Long inspectionId) {
        return jdbcTemplate.queryForList(
                """
                SELECT ia.id,
                       ia.file_id AS fileId,
                       f.original_name AS name,
                       CONCAT('/api/files/', f.id) AS url,
                       ia.sort_order AS sortOrder
                FROM inspection_attachment ia
                INNER JOIN file_upload f ON f.id = ia.file_id AND f.deleted = 0
                WHERE ia.inspection_id = ? AND ia.deleted = 0
                ORDER BY ia.sort_order ASC, ia.id ASC
                """,
                inspectionId
        );
    }

    private List<Map<String, Object>> listInspectionHistory(Long inspectionId) {
        return jdbcTemplate.queryForList(
                        """
                        SELECT history.id,
                               history.action,
                               history.content,
                               staff.real_name AS operatorName,
                               history.operated_at AS operatedAt
                        FROM inspection_record_history history
                        LEFT JOIN sys_user staff
                               ON staff.id = history.operator_id
                              AND staff.deleted = 0
                        WHERE history.inspection_id = ?
                          AND history.deleted = 0
                        ORDER BY history.operated_at ASC, history.id ASC
                        """,
                        inspectionId
                )
                .stream()
                .map(LinkedHashMap::new)
                .map(row -> (Map<String, Object>) row)
                .toList();
    }

    private Map<Long, List<Map<String, Object>>> listInspectionHistoryAttachments(Long inspectionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT attachment.inspection_history_id AS historyId,
                       file.id,
                       file.original_name AS name,
                       CONCAT('/api/files/', file.id) AS url,
                       file.mime_type AS mimeType,
                       file.file_size AS fileSize,
                       attachment.sort_order AS sortOrder
                FROM inspection_history_attachment attachment
                INNER JOIN inspection_record_history history
                        ON history.id = attachment.inspection_history_id
                       AND history.deleted = 0
                INNER JOIN file_upload file
                        ON file.id = attachment.file_id
                       AND file.deleted = 0
                       AND file.status = 'ENABLED'
                WHERE history.inspection_id = ?
                  AND attachment.deleted = 0
                  AND file.biz_type = 'INSPECTION'
                  AND file.biz_id = ?
                ORDER BY attachment.inspection_history_id ASC,
                         attachment.sort_order ASC,
                         attachment.id ASC
                """,
                inspectionId,
                inspectionId
        );
        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long historyId = JdbcMaps.longVal(row, "historyId");
            Map<String, Object> file = new LinkedHashMap<>(row);
            file.remove("historyId");
            grouped.computeIfAbsent(historyId, ignored -> new ArrayList<>()).add(file);
        }
        return grouped;
    }

    private ArrayList<Object> appendFilters(
            StringBuilder sql,
            StringBuilder countSql,
            String inspector,
            String target,
            String inspectionType,
            String result,
            String status,
            String startDate,
            String endDate
    ) {
        ArrayList<Object> args = new ArrayList<>();
        if (inspector != null && !inspector.isBlank()) {
            append(sql, countSql, " AND i.inspector LIKE ?");
            args.add("%" + inspector.trim() + "%");
        }
        if (target != null && !target.isBlank()) {
            append(sql, countSql, " AND (i.target LIKE ? OR i.area LIKE ?)");
            String value = "%" + target.trim() + "%";
            args.add(value);
            args.add(value);
        }
        if (inspectionType != null && !inspectionType.isBlank()) {
            append(sql, countSql, " AND i.inspection_type = ?");
            args.add(inspectionType.trim());
        }
        if (result != null && !result.isBlank()) {
            append(sql, countSql, " AND i.result = ?");
            args.add(result);
        }
        if (status != null && !status.isBlank()) {
            append(sql, countSql, " AND i.inspection_status = ?");
            args.add(status);
        }
        if (startDate != null && !startDate.isBlank()) {
            append(sql, countSql, " AND i.inspection_date >= ?");
            args.add(dateValue(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            append(sql, countSql, " AND i.inspection_date <= ?");
            args.add(dateValue(endDate));
        }
        return args;
    }

    private void append(StringBuilder sql, StringBuilder countSql, String clause) {
        sql.append(clause);
        if (!countSql.isEmpty()) {
            countSql.append(clause);
        }
    }

    private String dateValue(String value) {
        return value.substring(0, Math.min(value.length(), 10));
    }

    private Long generatedId(KeyHolder keyHolder) {
        if (keyHolder == null) return null;
        if (keyHolder.getKeyList() != null && !keyHolder.getKeyList().isEmpty()) {
            Map<String, Object> keys = keyHolder.getKeyList().get(0);
            Object fallback = keys.get("GENERATED_KEY");
            if (fallback == null) fallback = keys.get("id");
            if (fallback instanceof Number number) return number.longValue();
            if (fallback != null) {
                try {
                    return Long.parseLong(String.valueOf(fallback));
                } catch (NumberFormatException ignore) {
                    return null;
                }
            }
        }
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object rawKey = keys.get("GENERATED_KEY");
            if (rawKey == null) rawKey = keys.get("id");
            if (rawKey instanceof Number number) return number.longValue();
            if (rawKey != null) {
                try {
                    return Long.parseLong(String.valueOf(rawKey));
                } catch (NumberFormatException ignore) {
                    return null;
                }
            }
        }
        Object key = keyHolder.getKey();
        if (key == null) return null;
        if (key instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(key));
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private Long actorId(AuthPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            return 0L;
        }
        return principal.userId();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Long parseLong(Object... values) {
        for (Object value : values) {
            if (value == null) continue;
            if (value instanceof Number number) return number.longValue();
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) continue;
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignore) {
                throw new BusinessException(400, "Invalid attachment file id");
            }
        }
        return null;
    }

    private Integer parseInt(Object... values) {
        for (Object value : values) {
            if (value == null) continue;
            if (value instanceof Number number) return number.intValue();
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) continue;
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignore) {
                throw new BusinessException(400, "Invalid attachment sort order");
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeText(Object value) {
        String text = asString(value);
        if (text == null) return null;
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String fileExtension(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int index = normalized.lastIndexOf('.');
        return index < 0 ? "" : normalized.substring(index);
    }

    private record RequestedAttachment(Long fileId, Integer sortOrder) {
    }
}

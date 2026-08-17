package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.asset.application.AssetChargeBasisService;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.ModuleFieldSubmissionValidator;
import com.dehui.property.modules.inspection.application.InspectionInspectorValueResolver;
import com.dehui.property.modules.inspection.application.InspectionTypeValueResolver;
import com.dehui.property.modules.mobile.service.MobileCustomFieldValueService;
import com.dehui.property.modules.mobile.service.MobileStaffFileService;
import com.dehui.property.modules.visitor.application.VisitorApprovalService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MobileStaffController {

    // Compatibility evidence for the pre-V50 protected-file contract:
    // customFieldFileIds(body.get("customFields")); "INSPECTION", id, staffUserId;
    // "WORK_ORDER", id, staffUserId; "ENERGY_READING", id, staffUserId.

    private static final Set<String> INSPECTION_RESULTS = Set.of("NORMAL", "ABNORMAL", "PENDING");
    private static final Set<String> LEAD_STATUSES = Set.of(
            "NEW", "FOLLOWING", "VIEWING_SCHEDULED", "VIEWED", "CONVERTED", "INVALID"
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldProjectionService projectionService;
    private final MobileCustomFieldValueService customFieldValueService;
    private final MobileStaffFileService fileService;
    private final AssetChargeBasisService chargeBasisService;
    private final ObjectMapper objectMapper;
    private ModuleFieldSubmissionValidator moduleFieldSubmissionValidator;
    private VisitorApprovalService visitorApprovalService;

    public MobileStaffController(
            JdbcTemplate jdbcTemplate,
            CustomFieldProjectionService projectionService,
            MobileCustomFieldValueService customFieldValueService,
            MobileStaffFileService fileService,
            AssetChargeBasisService chargeBasisService,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectionService = projectionService;
        this.customFieldValueService = customFieldValueService;
        this.fileService = fileService;
        this.chargeBasisService = chargeBasisService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    void setModuleFieldSubmissionValidator(ModuleFieldSubmissionValidator service) {
        this.moduleFieldSubmissionValidator = service;
    }

    @Autowired(required = false)
    void setVisitorApprovalService(VisitorApprovalService service) {
        this.visitorApprovalService = service;
    }

    @GetMapping("/mobile/staff/workbench")
    public ApiResponse<Map<String, Object>> workbench(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        Long staffUserId = staff(principal);
        LinkedHashMap<String, Object> modules = new LinkedHashMap<>();
        if (has(principal, "workorder.staff.view")) {
            modules.put("workorder", jdbcTemplate.queryForMap(
                    """
                    SELECT COALESCE(SUM(CASE WHEN work_status IN ('ASSIGNED','SUBMITTED','CREATED') THEN 1 ELSE 0 END), 0) AS pendingCount,
                           COALESCE(SUM(CASE WHEN work_status = 'PROCESSING' THEN 1 ELSE 0 END), 0) AS processingCount
                    FROM work_order
                    WHERE assignee_id = ? AND deleted = 0
                    """,
                    staffUserId
            ));
        }
        if (has(principal, "inspection.staff.view")) {
            modules.put("inspection", jdbcTemplate.queryForMap(
                    """
                    SELECT COALESCE(SUM(CASE WHEN inspection_date = CURRENT_DATE THEN 1 ELSE 0 END), 0) AS todayCount,
                           COALESCE(SUM(CASE WHEN result = 'ABNORMAL' AND inspection_status <> 'CLOSED' THEN 1 ELSE 0 END), 0) AS abnormalCount
                    FROM inspection_record
                    WHERE inspector_user_id = ? AND deleted = 0
                    """,
                    staffUserId
            ));
        }
        if (has(principal, "energy.staff.view")) {
            modules.put("energy", jdbcTemplate.queryForMap(
                    """
                    SELECT COALESCE(SUM(CASE WHEN abnormal_flag = 1 AND abnormal_status = 'PENDING' THEN 1 ELSE 0 END), 0) AS abnormalCount
                    FROM energy_reading
                    WHERE deleted = 0
                    """
            ));
        }
        if (has(principal, "meeting.staff.view")) {
            modules.put("meeting", jdbcTemplate.queryForMap(
                    """
                    SELECT COALESCE(SUM(CASE
                               WHEN DATE(start_time) = CURRENT_DATE
                                AND booking_status NOT IN ('CANCELLED','REJECTED')
                               THEN 1 ELSE 0 END), 0) AS todayCount
                    FROM meeting_booking
                    WHERE deleted = 0
                    """
            ));
        }
        if (has(principal, "visitor.staff.view")) {
            modules.put("visitor", jdbcTemplate.queryForMap(
                    """
                    SELECT COALESCE(SUM(CASE
                               WHEN appointment_status IN ('PENDING','REGISTERED')
                                AND DATE(visit_time) >= CURRENT_DATE THEN 1 ELSE 0 END), 0) AS pendingCount
                    FROM visitor_appointment
                    WHERE deleted = 0
                    """
            ));
        }
        if (has(principal, "investment.lead.staff.view")) {
            modules.put("investmentLead", jdbcTemplate.queryForMap(
                    """
                    SELECT COALESCE(SUM(CASE WHEN lead_status IN ('NEW','FOLLOWING','VIEWING_SCHEDULED') THEN 1 ELSE 0 END), 0) AS pendingCount
                    FROM investment_lead
                    WHERE mobile_user_id IS NOT NULL AND deleted = 0
                    """
            ));
        }
        return ApiResponse.success(Map.of(
                "profile", MobileApiSupport.profile(jdbcTemplate, principal),
                "modules", modules
        ));
    }

    @GetMapping("/mobile/staff/inspection-types")
    public ApiResponse<List<Map<String, Object>>> inspectionTypes(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        require(principal, "inspection.staff.view");
        staff(principal);
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT code, name, description
                FROM inspection_type
                WHERE status = 'ENABLED' AND deleted = 0
                  AND CHAR_LENGTH(TRIM(code)) <= 32
                ORDER BY sort_order, id
                """
        ));
    }

    @GetMapping("/mobile/staff/inspection-options")
    public ApiResponse<Map<String, Object>> inspectionOptions(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        require(principal, "inspection.staff.view");
        Long currentStaffUserId = staff(principal);
        Map<String, Object> currentInspector = resolveInspectionInspector(null, currentStaffUserId);
        return ApiResponse.success(Map.of(
                "currentInspectorCode", String.valueOf(currentInspector.get("inspectorCode")),
                "inspectors", inspectionInspectorOptions(),
                "areas", inspectionAreaOptions()
        ));
    }

    @GetMapping("/mobile/staff/inspections")
    public ApiResponse<PageResponse<Map<String, Object>>> inspections(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) String inspectionType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        require(principal, "inspection.staff.view");
        Long staffUserId = staff(principal);
        StringBuilder where = new StringBuilder(" WHERE inspection.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (Boolean.TRUE.equals(mine)) {
            where.append(" AND inspection.inspector_user_id = ?");
            args.add(staffUserId);
        }
        appendEquals(where, args, "inspection.inspection_type", inspectionType);
        appendEquals(where, args, "inspection.result", result);
        appendEquals(where, args, "inspection.inspection_status", status);
        if (text(startDate) != null) {
            where.append(" AND inspection.inspection_date >= ?");
            args.add(text(startDate));
        }
        if (text(endDate) != null) {
            where.append(" AND inspection.inspection_date <= ?");
            args.add(text(endDate));
        }
        PageResponse<Map<String, Object>> resultPage = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT inspection.id, inspection.code,
                       inspection.inspection_date AS inspectionDate,
                       inspection.inspector,
                       inspection.inspection_type AS inspectionType,
                       type_record.name AS inspectionTypeName,
                       inspection.area AS location,
                       inspection.area AS area,
                       inspection.target AS inspectionObject,
                       inspection.target AS target,
                       inspection.result AS inspectionResult,
                       inspection.result AS result,
                       inspection.problem_description AS problemDescription,
                       inspection.action_taken AS handlingMeasures,
                       inspection.action_taken AS actionTaken,
                       inspection.inspection_status AS status,
                       inspection.remark,
                       inspection.created_at AS createdAt,
                       inspection.updated_at AS updatedAt
                FROM inspection_record inspection
                LEFT JOIN inspection_type type_record
                  ON type_record.code = inspection.inspection_type AND type_record.deleted = 0
                """ + where + " ORDER BY inspection.inspection_date DESC, inspection.id DESC",
                "SELECT COUNT(*) FROM inspection_record inspection" + where,
                args,
                page,
                pageSize
        );
        return ApiResponse.success(projectionService.attach("inspections", resultPage));
    }

    @GetMapping("/mobile/staff/inspections/{id}")
    public ApiResponse<Map<String, Object>> inspectionDetail(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        require(principal, "inspection.staff.view");
        staff(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT inspection.id, inspection.code,
                       inspection.inspection_date AS inspectionDate,
                       inspection.inspector,
                       inspection.inspection_type AS inspectionType,
                       type_record.name AS inspectionTypeName,
                       inspection.area AS location,
                       inspection.area AS area,
                       inspection.target AS inspectionObject,
                       inspection.target AS target,
                       inspection.result AS inspectionResult,
                       inspection.result AS result,
                       inspection.problem_description AS problemDescription,
                       inspection.action_taken AS handlingMeasures,
                       inspection.action_taken AS actionTaken,
                       inspection.inspection_status AS status,
                       inspection.remark,
                       inspection.created_at AS createdAt,
                       inspection.updated_at AS updatedAt
                FROM inspection_record inspection
                LEFT JOIN inspection_type type_record
                  ON type_record.code = inspection.inspection_type AND type_record.deleted = 0
                WHERE inspection.id = ? AND inspection.deleted = 0
                """,
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "巡检记录不存在");
        Map<String, Object> detail = new LinkedHashMap<>(
                projectionService.attach("inspections", rows).getFirst()
        );
        List<Map<String, Object>> attachments = inspectionAttachments(id);
        detail.put("attachments", attachments);
        detail.put("images", attachments);
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                """
                SELECT history.id, history.action, history.content,
                       staff.real_name AS operatorName, history.operated_at AS operatedAt
                FROM inspection_record_history history
                LEFT JOIN sys_user staff ON staff.id = history.operator_id AND staff.deleted = 0
                WHERE history.inspection_id = ? AND history.deleted = 0
                ORDER BY history.operated_at, history.id
                """,
                id
        );
        Map<Long, List<Map<String, Object>>> resolutionImages = inspectionHistoryAttachments(id);
        history.forEach(record -> record.put(
                "resolutionImages",
                resolutionImages.getOrDefault(JdbcMaps.longVal(record, "id"), List.of())
        ));
        detail.put("history", history);
        return ApiResponse.success(detail);
    }

    @PostMapping("/mobile/staff/inspections")
    @Transactional
    public ApiResponse<Map<String, Object>> createInspection(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        require(principal, "inspection.staff.create");
        Long staffUserId = staff(principal);
        Map<String, Object> canonical = new LinkedHashMap<>(body == null ? Map.of() : body);
        alias(canonical, "area", "location");
        alias(canonical, "target", "inspectionObject");
        alias(canonical, "result", "inspectionResult");
        alias(canonical, "actionTaken", "handlingMeasures");
        if (!canonical.containsKey("inspector")) {
            String legacyInspectorCode = textValue(canonical, "inspectorCode");
            String legacyInspectorName = legacyInspectorCode == null
                    ? staffName(staffUserId)
                    : JdbcMaps.requiredStr(
                            resolveInspectionInspector(legacyInspectorCode, staffUserId),
                            "巡检人档案无效",
                            "inspectorName"
                    );
            canonical.put("inspector", legacyInspectorName);
        }
        if (moduleFieldSubmissionValidator != null) {
            moduleFieldSubmissionValidator.validateMobileCreate(
                    "inspections", canonical, principal,
                    Set.of("location", "inspectionObject", "inspectionResult", "handlingMeasures",
                            "attachmentFileIds", "fileIds", "inspectorCode")
            );
        }
        String inspectorName = InspectionInspectorValueResolver.resolve(
                required(canonical, "请填写巡检人", "inspector")
        );
        Long inspectorUserId = staffUserId;
        String type = InspectionTypeValueResolver.resolve(
                jdbcTemplate,
                required(canonical, "请填写巡检类型", "inspectionType"),
                null
        );
        String result = normalizedInspectionResult(canonical);
        String problem = textValue(canonical, "problemDescription");
        if ("ABNORMAL".equals(result) && problem == null) {
            throw new BusinessException(400, "异常巡检必须填写问题描述");
        }
        LocalDate inspectionDate = dateValue(canonical, "inspectionDate", LocalDate.now());
        String location = required(canonical, "请填写巡检区域或地点", "area");
        validateInspectionArea(location);
        String target = required(canonical, "请填写巡检对象", "target");
        String measures = textValue(canonical, "actionTaken");
        String remark = textValue(canonical, "remark");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO inspection_record (
                        code, inspection_date, inspector, inspector_user_id, inspection_type,
                        area, target, result, problem_description, action_taken,
                        inspection_status, status, created_by, updated_by, remark, deleted
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', 'ACTIVE', ?, ?, ?, 0)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, JdbcMaps.code("INSP"));
            statement.setDate(2, Date.valueOf(inspectionDate));
            statement.setString(3, inspectorName);
            statement.setLong(4, inspectorUserId);
            statement.setString(5, type);
            statement.setString(6, location);
            statement.setString(7, target);
            statement.setString(8, result);
            statement.setString(9, problem);
            statement.setString(10, measures);
            statement.setLong(11, staffUserId);
            statement.setLong(12, staffUserId);
            statement.setString(13, remark);
            return statement;
        }, keyHolder);
        Long id = keyHolder.getKey().longValue();
        attachInspectionFiles(id, staffUserId, inspectionFileIds(canonical));
        fileService.claimDraftFiles(
                principal, customFieldFileIds(canonical.get("customFields")), "INSPECTION", id, "inspections"
        );
        customFieldValueService.save("inspections", id, canonical.get("customFields"), staffUserId);
        inspectionHistory(id, "CREATE", remark, staffUserId);
        return ApiResponse.success(Map.of("id", id));
    }

    @PatchMapping("/mobile/staff/inspections/{id}")
    @Transactional
    public ApiResponse<Void> updateInspection(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        require(principal, "inspection.staff.update");
        Long staffUserId = staff(principal);
        List<Map<String, Object>> currentRows = jdbcTemplate.queryForList(
                """
                SELECT result, problem_description AS problemDescription,
                       action_taken AS handlingMeasures, inspection_status AS inspectionStatus
                FROM inspection_record
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                id
        );
        if (currentRows.isEmpty()) throw new BusinessException(404, "巡检记录不存在");
        Map<String, Object> current = currentRows.getFirst();
        if ("CLOSED".equals(current.get("inspectionStatus"))) {
            throw new BusinessException(409, "已关闭巡检不能继续修改");
        }
        String result = textValue(body, "inspectionResult", "result");
        result = result == null ? String.valueOf(current.get("result")) : result.toUpperCase(Locale.ROOT);
        if (!INSPECTION_RESULTS.contains(result)) throw new BusinessException(400, "巡检结果无效");
        String problem = textValue(body, "problemDescription");
        if (problem == null) problem = text(current.get("problemDescription"));
        if ("ABNORMAL".equals(result) && problem == null) {
            throw new BusinessException(400, "异常巡检必须填写问题描述");
        }
        String measures = textValue(body, "handlingMeasures", "actionTaken");
        if (measures == null) measures = text(current.get("handlingMeasures"));
        String feedback = textValue(body, "feedback", "remark");
        jdbcTemplate.update(
                """
                UPDATE inspection_record
                SET result = ?, problem_description = ?, action_taken = ?,
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                result,
                problem,
                measures,
                staffUserId,
                id
        );
        attachInspectionFiles(id, staffUserId, inspectionFileIds(body));
        if (body.containsKey("customFields")) {
            fileService.claimDraftFiles(
                    principal, customFieldFileIds(body.get("customFields")), "INSPECTION", id, "inspections"
            );
            customFieldValueService.save("inspections", id, body.get("customFields"), staffUserId);
        }
        Long historyId = inspectionHistory(id, "UPDATE", feedback == null ? measures : feedback, staffUserId);
        attachInspectionHistoryFiles(historyId, id, staffUserId, inspectionResolutionFileIds(body));
        return ApiResponse.success();
    }

    @PostMapping("/mobile/staff/inspections/{id}/close")
    @Transactional
    public ApiResponse<Void> closeInspection(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        require(principal, "inspection.staff.update");
        Long staffUserId = staff(principal);
        int updated = jdbcTemplate.update(
                """
                UPDATE inspection_record
                SET inspection_status = 'CLOSED',
                    action_taken = COALESCE(?, action_taken),
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                  AND inspection_status <> 'CLOSED'
                """,
                textValue(body, "handlingMeasures"),
                staffUserId,
                id
        );
        if (updated == 0) {
            Long exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inspection_record WHERE id = ? AND deleted = 0",
                    Long.class,
                    id
            );
            if (exists == null || exists == 0) throw new BusinessException(404, "巡检记录不存在");
            return ApiResponse.success();
        }
        Long historyId = inspectionHistory(
                id,
                "CLOSE",
                textValue(body, "feedback", "handlingMeasures"),
                staffUserId
        );
        attachInspectionHistoryFiles(historyId, id, staffUserId, inspectionResolutionFileIds(body));
        return ApiResponse.success();
    }

    @GetMapping("/mobile/staff/energy/summary")
    public ApiResponse<Map<String, Object>> energySummary(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String periodMonth
    ) {
        require(principal, "energy.staff.view");
        staff(principal);
        String month = month(periodMonth);
        List<Map<String, Object>> byType = jdbcTemplate.queryForList(
                """
                SELECT meter.meter_type AS meterType,
                       COALESCE(SUM(reading.usage_amount), 0) AS usageAmount,
                       COALESCE(SUM(reading.settlement_amount), 0) AS settlementAmount,
                       COALESCE(SUM(reading.abnormal_flag), 0) AS abnormalCount
                FROM energy_reading reading
                JOIN energy_meter meter ON meter.id = reading.meter_id AND meter.deleted = 0
                WHERE reading.period_month = ? AND reading.deleted = 0
                GROUP BY meter.meter_type
                ORDER BY meter.meter_type
                """,
                month
        );
        List<Map<String, Object>> byTenant = jdbcTemplate.queryForList(
                """
                SELECT tenant.id, tenant.name AS tenantName,
                       COALESCE(SUM(reading.usage_amount), 0) AS usageAmount,
                       COALESCE(SUM(reading.settlement_amount), 0) AS settlementAmount,
                       COALESCE(SUM(reading.abnormal_flag), 0) AS abnormalCount
                FROM energy_reading reading
                JOIN energy_meter meter ON meter.id = reading.meter_id AND meter.deleted = 0
                JOIN energy_meter_binding binding
                  ON binding.id = (
                      SELECT candidate.id
                      FROM energy_meter_binding candidate
                      WHERE candidate.meter_id = meter.id
                        AND candidate.deleted = 0
                        AND candidate.effective_from <= reading.reading_date
                        AND (candidate.effective_to IS NULL OR candidate.effective_to >= reading.reading_date)
                      ORDER BY candidate.effective_from DESC, candidate.id DESC
                      LIMIT 1
                  )
                JOIN tenant ON tenant.id = binding.tenant_id AND tenant.deleted = 0
                WHERE reading.period_month = ? AND reading.deleted = 0
                GROUP BY tenant.id, tenant.name
                ORDER BY settlementAmount DESC, tenant.id
                """,
                month
        );
        return ApiResponse.success(Map.of("periodMonth", month, "byType", byType, "byTenant", byTenant));
    }

    @GetMapping("/mobile/staff/energy/meters")
    public ApiResponse<PageResponse<Map<String, Object>>> energyMeters(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        require(principal, "energy.staff.view");
        staff(principal);
        StringBuilder where = new StringBuilder(" WHERE meter.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        appendEquals(where, args, "meter.meter_type", meterType);
        appendLong(where, args, "binding.tenant_id", tenantId);
        appendLong(where, args, "meter.building_id", buildingId);
        appendLong(where, args, "meter.floor_id", floorId);
        appendLong(where, args, "meter.room_id", roomId);
        PageResponse<Map<String, Object>> pageResult = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT meter.id, meter.meter_no AS meterNo, meter.meter_name AS meterName,
                       meter.meter_type AS meterType, meter.unit, meter.multiplier,
                       meter.location_description AS location,
                       building.name AS buildingName, floor.floor_no AS floorName,
                       room.room_no AS roomName, tenant.name AS tenantName,
                       binding.subject_type AS subjectType, meter.status
                FROM energy_meter meter
                LEFT JOIN energy_meter_binding binding
                  ON binding.id = (
                      SELECT candidate.id
                      FROM energy_meter_binding candidate
                      WHERE candidate.meter_id = meter.id
                        AND candidate.deleted = 0
                        AND candidate.status = 'ACTIVE'
                        AND candidate.effective_from <= CURRENT_DATE
                        AND (candidate.effective_to IS NULL OR candidate.effective_to >= CURRENT_DATE)
                      ORDER BY candidate.effective_from DESC, candidate.id DESC
                      LIMIT 1
                  )
                LEFT JOIN tenant ON tenant.id = binding.tenant_id AND tenant.deleted = 0
                LEFT JOIN building ON building.id = meter.building_id AND building.deleted = 0
                LEFT JOIN building_floor floor ON floor.id = meter.floor_id AND floor.deleted = 0
                LEFT JOIN building_room room ON room.id = meter.room_id AND room.deleted = 0
                """ + where + " ORDER BY meter.meter_no, meter.id",
                """
                SELECT COUNT(*)
                FROM energy_meter meter
                LEFT JOIN energy_meter_binding binding
                  ON binding.id = (
                      SELECT candidate.id
                      FROM energy_meter_binding candidate
                      WHERE candidate.meter_id = meter.id
                        AND candidate.deleted = 0
                        AND candidate.status = 'ACTIVE'
                        AND candidate.effective_from <= CURRENT_DATE
                        AND (candidate.effective_to IS NULL OR candidate.effective_to >= CURRENT_DATE)
                      ORDER BY candidate.effective_from DESC, candidate.id DESC
                      LIMIT 1
                  )
                """ + where,
                args,
                page,
                pageSize
        );
        return ApiResponse.success(pageResult);
    }

    @GetMapping("/mobile/staff/energy/readings")
    public ApiResponse<PageResponse<Map<String, Object>>> energyReadings(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String periodMonth,
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) Boolean abnormal,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        require(principal, "energy.staff.view");
        staff(principal);
        StringBuilder where = new StringBuilder(" WHERE reading.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        appendEquals(where, args, "reading.period_month", periodMonth);
        appendEquals(where, args, "meter.meter_type", meterType);
        appendLong(where, args, "binding.tenant_id", tenantId);
        if (abnormal != null) {
            where.append(" AND reading.abnormal_flag = ?");
            args.add(Boolean.TRUE.equals(abnormal) ? 1 : 0);
        }
        PageResponse<Map<String, Object>> resultPage = JdbcPagination.query(
                jdbcTemplate,
                energyReadingSelect() + where + " ORDER BY reading.period_month DESC, reading.reading_date DESC, reading.id DESC",
                energyReadingCount() + where,
                args,
                page,
                pageSize
        );
        return ApiResponse.success(projectionService.attach("energy-records", resultPage));
    }

    @PostMapping("/mobile/staff/energy/readings")
    @Transactional
    public ApiResponse<Map<String, Object>> createEnergyReading(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        require(principal, "energy.staff.reading.create");
        Long staffUserId = staff(principal);
        Long meterId = JdbcMaps.requiredLong(body, "请选择表具", "meterId");
        String periodMonth = month(required(body, "请选择抄表月份", "periodMonth"));
        LocalDate readingDate = dateValue(body, "readingDate", LocalDate.now());
        BigDecimal currentReading = decimal(body.get("currentReading"));
        if (currentReading.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "本次读数不能小于0");
        }
        List<Map<String, Object>> meters = jdbcTemplate.queryForList(
                """
                SELECT id, meter_no AS meterNo, meter_name AS meterName, meter_type AS meterType,
                       unit, multiplier, initial_reading AS initialReading, status
                FROM energy_meter
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                meterId
        );
        if (meters.isEmpty()) throw new BusinessException(404, "表具不存在");
        Map<String, Object> meter = meters.getFirst();
        if (!Set.of("ACTIVE", "ENABLED").contains(String.valueOf(meter.get("status")))) {
            throw new BusinessException(409, "停用表具不能抄表");
        }
        requireMeterBinding(meterId, readingDate);
        BigDecimal previous = previousReading(meterId, periodMonth, decimal(meter.get("initialReading")));
        String resetReason = textValue(body, "resetReason");
        boolean reset = currentReading.compareTo(previous) < 0;
        if (reset && resetReason == null) {
            throw new BusinessException(400, "本次读数小于上期读数，必须填写表具归零或更换原因");
        }
        BigDecimal multiplier = decimal(meter.get("multiplier"));
        BigDecimal usage = (reset ? currentReading : currentReading.subtract(previous))
                .multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal previousUsage = previousUsage(meterId, periodMonth);
        boolean spike = !reset
                && previousUsage.compareTo(BigDecimal.ZERO) > 0
                && usage.compareTo(previousUsage.multiply(BigDecimal.valueOf(3))) > 0;
        int abnormalFlag = reset || spike ? 1 : 0;
        String abnormalReason = reset ? "METER_RESET" : (spike ? "USAGE_SPIKE" : null);
        BigDecimal unitPrice = unitPrice(periodMonth, String.valueOf(meter.get("meterType")));
        BigDecimal amount = usage.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        List<Long> fileIds = ids(body, "attachmentFileIds", "fileIds");
        Long attachmentFileId = fileIds.isEmpty() ? null : fileIds.getFirst();
        String code = JdbcMaps.code("ENR");
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO energy_reading (
                        code, meter_id, period_month, reading_date,
                        previous_reading, current_reading, usage_amount, multiplier,
                        unit_price, settlement_amount, operator_name, reading_source,
                        attachment_file_id, reset_reason, bill_status,
                        abnormal_flag, abnormal_reason, abnormal_status,
                        status, created_by, updated_by, remark, deleted
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL',
                            ?, ?, 'NOT_GENERATED', ?, ?, 'PENDING',
                            'ACTIVE', ?, ?, ?, 0)
                    """,
                    code,
                    meterId,
                    periodMonth,
                    Date.valueOf(readingDate),
                    previous,
                    currentReading,
                    usage,
                    multiplier,
                    unitPrice,
                    amount,
                    staffName(staffUserId),
                    attachmentFileId,
                    resetReason,
                    abnormalFlag,
                    abnormalReason,
                    staffUserId,
                    staffUserId,
                    textValue(body, "remark")
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "该表具同月份已存在抄表记录");
        }
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM energy_reading WHERE code = ? AND deleted = 0",
                Long.class,
                code
        );
        if (attachmentFileId != null) {
            fileService.claimDraftFiles(List.of(attachmentFileId), "ENERGY_READING", id, staffUserId);
        }
        fileService.claimDraftFiles(customFieldFileIds(body.get("customFields")), "ENERGY_READING", id, staffUserId);
        customFieldValueService.save("energy-records", id, body.get("customFields"), staffUserId);
        auditEnergyReading(id, staffUserId);
        Long basisId = chargeBasisService.confirmEnergyBasis(id, staffUserId);
        return ApiResponse.success(Map.of(
                "id", id,
                "previousReading", previous,
                "usageAmount", usage,
                "settlementAmount", amount,
                "abnormal", abnormalFlag == 1,
                "abnormalReason", abnormalReason == null ? "" : abnormalReason,
                "chargeBasisId", basisId
        ));
    }

    @GetMapping("/mobile/staff/meetings/{id}")
    public ApiResponse<Map<String, Object>> meetingDetail(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        require(principal, "meeting.staff.view");
        staff(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT booking.id, booking.code AS bookingNumber,
                       room.name AS meetingRoomName, room.location,
                       tenant.name AS tenantName,
                       booking.contact_name AS applicantName,
                       booking.contact_phone AS applicantPhone,
                       booking.start_time AS startTime, booking.end_time AS endTime,
                       booking.participant_count AS participantCount,
                       booking.purpose, booking.booking_status AS status,
                       booking.amount, booking.created_at AS createdAt, booking.remark,
                       booking.review_reply AS reviewReply,
                       booking.reviewed_at AS reviewedAt
                FROM meeting_booking booking
                JOIN meeting_room room ON room.id = booking.meeting_room_id AND room.deleted = 0
                LEFT JOIN tenant ON tenant.id = booking.tenant_id AND tenant.deleted = 0
                WHERE booking.id = ? AND booking.deleted = 0
                """,
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "会议预约不存在");
        return ApiResponse.success(projectionService.attach("meeting-bookings", rows).getFirst());
    }

    @PostMapping("/mobile/staff/meetings/{id}/status")
    @Transactional
    public ApiResponse<Void> updateMeetingStatus(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        staff(principal);
        String target = required(body, "请选择会议预约状态", "status").toUpperCase(Locale.ROOT);
        String capability = switch (target) {
            case "CONFIRMED" -> "meeting.staff.confirm";
            case "CANCELLED" -> "meeting.staff.cancel";
            case "COMPLETED" -> "meeting.staff.complete";
            default -> throw new BusinessException(400, "不支持的会议预约状态");
        };
        require(principal, capability);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT booking_status AS status FROM meeting_booking WHERE id = ? AND deleted = 0 FOR UPDATE",
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "会议预约不存在");
        String current = String.valueOf(rows.getFirst().get("status"));
        if (current.equals(target)) return ApiResponse.success();
        Set<String> allowed = switch (target) {
            case "CONFIRMED" -> Set.of("PENDING", "BOOKED");
            case "CANCELLED" -> Set.of("PENDING", "BOOKED", "CONFIRMED");
            case "COMPLETED" -> Set.of("CONFIRMED");
            default -> Set.of();
        };
        if (!allowed.contains(current)) {
            throw new BusinessException(409, "当前会议预约状态不能执行该操作");
        }
        Long staffUserId = staff(principal);
        String remark = textValue(body, "remark");
        if ("CONFIRMED".equals(target)) {
            String reviewReply = textValue(body, "reviewReply", "remark");
            if (reviewReply == null) reviewReply = "预约已审核通过";
            if (reviewReply.length() > 500) {
                throw new BusinessException(400, "预约回复不能超过500个字符");
            }
            jdbcTemplate.update(
                    """
                    UPDATE meeting_booking
                    SET booking_status = ?, updated_by = ?, remark = COALESCE(?, remark),
                        review_reply = ?, reviewed_by = ?, reviewed_at = NOW()
                    WHERE id = ? AND deleted = 0 AND booking_status = ?
                    """,
                    target,
                    staffUserId,
                    remark,
                    reviewReply,
                    staffUserId,
                    id,
                    current
            );
        } else {
            jdbcTemplate.update(
                    """
                    UPDATE meeting_booking
                    SET booking_status = ?, updated_by = ?,
                        remark = COALESCE(?, remark)
                    WHERE id = ? AND deleted = 0 AND booking_status = ?
                    """,
                    target,
                    staffUserId,
                    remark,
                    id,
                    current
            );
        }
        if ("CONFIRMED".equals(target)) {
            BigDecimal amount = jdbcTemplate.queryForObject(
                    "SELECT amount FROM meeting_booking WHERE id = ?",
                    BigDecimal.class,
                    id
            );
            Long tenantId = jdbcTemplate.queryForObject(
                    "SELECT tenant_id FROM meeting_booking WHERE id = ?",
                    Long.class,
                    id
            );
            if (tenantId != null && amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                chargeBasisService.confirmMeetingBasis(id, staffUserId);
            }
        }
        return ApiResponse.success();
    }

    @GetMapping("/mobile/staff/visitors")
    public ApiResponse<PageResponse<Map<String, Object>>> visitors(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        require(principal, "visitor.staff.view");
        staff(principal);
        StringBuilder where = new StringBuilder(" WHERE appointment.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        appendEquals(where, args, "appointment.appointment_status", status);
        if (text(date) != null) {
            where.append(" AND DATE(appointment.visit_time) = ?");
            args.add(text(date));
        }
        return ApiResponse.success(JdbcPagination.query(
                jdbcTemplate,
                visitorSelect() + where
                        + " ORDER BY CASE WHEN appointment.appointment_status = 'PENDING' THEN 0 ELSE 1 END,"
                        + " appointment.created_at DESC, appointment.id DESC",
                "SELECT COUNT(*) FROM visitor_appointment appointment" + where,
                args,
                page,
                pageSize
        ));
    }

    @GetMapping("/mobile/staff/visitors/{id}")
    public ApiResponse<Map<String, Object>> visitorDetail(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        require(principal, "visitor.staff.view");
        staff(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                visitorSelect() + " WHERE appointment.id = ? AND appointment.deleted = 0",
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "访客预约不存在");
        Map<String, Object> detail = new LinkedHashMap<>(
                projectionService.attach("visitors", rows).getFirst()
        );
        detail.put("passRecords", jdbcTemplate.queryForList(
                """
                SELECT pass.pass_type AS passType, pass.pass_time AS passTime,
                       staff.real_name AS operatorName
                FROM visitor_pass_record pass
                LEFT JOIN sys_user staff ON staff.id = pass.operator_id AND staff.deleted = 0
                WHERE pass.appointment_id = ? AND pass.deleted = 0
                ORDER BY pass.pass_time, pass.id
                """,
                id
        ));
        return ApiResponse.success(detail);
    }

    @PostMapping("/mobile/staff/visitors/{id}/approve")
    public ApiResponse<Map<String, Object>> visitorApprove(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        require(principal, "visitor.staff.verify");
        return ApiResponse.success(visitorApprovalService().approveByStaff(
                id,
                body == null ? null : JdbcMaps.str(body, "reviewRemark"),
                staff(principal)
        ));
    }

    @PostMapping("/mobile/staff/visitors/{id}/reject")
    public ApiResponse<Map<String, Object>> visitorReject(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        require(principal, "visitor.staff.verify");
        return ApiResponse.success(visitorApprovalService().rejectByStaff(
                id,
                body == null ? null : JdbcMaps.str(body, "reviewRemark"),
                staff(principal)
        ));
    }

    @PostMapping("/mobile/staff/visitors/{id}/qr-code")
    public ApiResponse<Map<String, Object>> visitorQrCode(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        require(principal, "visitor.staff.verify");
        return ApiResponse.success(visitorApprovalService().attachStaffQrCode(
                id,
                JdbcMaps.longVal(body, "fileId"),
                staff(principal)
        ));
    }

    @PostMapping("/mobile/staff/visitors/{id}/enter")
    @Transactional
    public ApiResponse<Void> visitorEnter(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        require(principal, "visitor.staff.verify");
        Long staffUserId = staff(principal);
        List<Map<String, Object>> rows = lockVisitor(id);
        String current = String.valueOf(rows.getFirst().get("status"));
        if ("ENTERED".equals(current)) return ApiResponse.success();
        if (!"REGISTERED".equals(current)
                || truthy(rows.getFirst().get("expired"))) {
            throw new BusinessException(409, "当前访客预约不能核验到访");
        }
        jdbcTemplate.update(
                "UPDATE visitor_appointment SET appointment_status = 'ENTERED', updated_by = ? WHERE id = ? AND deleted = 0",
                staffUserId,
                id
        );
        visitorPass(id, "ENTER", staffUserId);
        return ApiResponse.success();
    }

    @PostMapping("/mobile/staff/visitors/{id}/leave")
    @Transactional
    public ApiResponse<Void> visitorLeave(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        require(principal, "visitor.staff.verify");
        Long staffUserId = staff(principal);
        List<Map<String, Object>> rows = lockVisitor(id);
        String current = String.valueOf(rows.getFirst().get("status"));
        if ("LEFT".equals(current)) return ApiResponse.success();
        if (!"ENTERED".equals(current)) {
            throw new BusinessException(409, "访客尚未到访，不能登记离场");
        }
        jdbcTemplate.update(
                "UPDATE visitor_appointment SET appointment_status = 'LEFT', updated_by = ? WHERE id = ? AND deleted = 0",
                staffUserId,
                id
        );
        visitorPass(id, "LEAVE", staffUserId);
        return ApiResponse.success();
    }

    @GetMapping("/mobile/staff/investment-leads")
    public ApiResponse<PageResponse<Map<String, Object>>> investmentLeads(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        require(principal, "investment.lead.staff.view");
        staff(principal);
        StringBuilder where = new StringBuilder(
                " WHERE lead_record.deleted = 0 AND lead_record.mobile_user_id IS NOT NULL "
        );
        List<Object> args = new ArrayList<>();
        appendEquals(where, args, "lead_record.lead_status", status);
        return ApiResponse.success(projectionService.attach(
                "investment-leads",
                JdbcPagination.query(
                        jdbcTemplate,
                        investmentLeadSelect() + where + " ORDER BY lead_record.created_at DESC, lead_record.id DESC",
                        "SELECT COUNT(*) FROM investment_lead lead_record" + where,
                        args,
                        page,
                        pageSize
                )
        ));
    }

    @GetMapping("/mobile/staff/investment-leads/{id}")
    public ApiResponse<Map<String, Object>> investmentLeadDetail(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        require(principal, "investment.lead.staff.view");
        staff(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                investmentLeadSelect()
                        + " WHERE lead_record.id = ? AND lead_record.mobile_user_id IS NOT NULL AND lead_record.deleted = 0",
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "看房预约不存在");
        Map<String, Object> detail = new LinkedHashMap<>(
                projectionService.attach("investment-leads", rows).getFirst()
        );
        detail.put("followRecords", jdbcTemplate.queryForList(
                """
                SELECT follow.follow_type AS followType, follow.content,
                       follow.next_follow_at AS nextFollowAt,
                       staff.real_name AS operatorName, follow.created_at AS createdAt
                FROM investment_follow_record follow
                LEFT JOIN sys_user staff ON staff.id = follow.follower_id AND staff.deleted = 0
                WHERE follow.lead_id = ? AND follow.deleted = 0
                ORDER BY follow.created_at DESC, follow.id DESC
                """,
                id
        ));
        return ApiResponse.success(detail);
    }

    @PostMapping("/mobile/staff/investment-leads/{id}/follow")
    @Transactional
    public ApiResponse<Void> followInvestmentLead(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        require(principal, "investment.lead.staff.follow");
        Long staffUserId = staff(principal);
        requireMobileLead(id);
        jdbcTemplate.update(
                """
                INSERT INTO investment_follow_record (
                    lead_id, follower_id, follow_type, content, next_follow_at,
                    status, created_by, updated_by, deleted
                )
                VALUES (?, ?, ?, ?, ?, 'ENABLED', ?, ?, 0)
                """,
                id,
                staffUserId,
                textValue(body, "followType") == null
                        ? "OTHER"
                        : textValue(body, "followType").toUpperCase(Locale.ROOT),
                required(body, "请填写跟进记录", "content"),
                JdbcMaps.datetime(body, null, "nextFollowAt"),
                staffUserId,
                staffUserId
        );
        return ApiResponse.success();
    }

    @PostMapping("/mobile/staff/investment-leads/{id}/status")
    @Transactional
    public ApiResponse<Void> updateInvestmentLeadStatus(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        String target = required(body, "请选择线索状态", "status").toUpperCase(Locale.ROOT);
        if (!LEAD_STATUSES.contains(target) || "NEW".equals(target)) {
            throw new BusinessException(400, "不支持的线索状态");
        }
        require(principal, "CONVERTED".equals(target)
                ? "investment.lead.staff.convert"
                : "investment.lead.staff.follow");
        Long staffUserId = staff(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT lead_status AS status
                FROM investment_lead
                WHERE id = ? AND mobile_user_id IS NOT NULL AND deleted = 0
                FOR UPDATE
                """,
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "看房预约不存在");
        String current = normalizeLeadStatus(rows.getFirst().get("status"));
        if (current.equals(target)) return ApiResponse.success();
        if (!leadTransitions(current).contains(target)) {
            throw new BusinessException(409, "当前线索状态不能执行该操作");
        }
        jdbcTemplate.update(
                """
                UPDATE investment_lead
                SET lead_status = ?, status_changed_at = NOW(),
                    status_changed_by = ?, updated_by = ?
                WHERE id = ? AND deleted = 0 AND lead_status = ?
                """,
                target,
                staffUserId,
                staffUserId,
                id,
                rows.getFirst().get("status")
        );
        jdbcTemplate.update(
                """
                INSERT INTO investment_lead_status_history (
                    lead_id, from_status, to_status, operator_id, changed_at,
                    remark, status, created_by, updated_by, deleted
                )
                VALUES (?, ?, ?, ?, NOW(), ?, 'ENABLED', ?, ?, 0)
                """,
                id,
                current,
                target,
                staffUserId,
                textValue(body, "remark"),
                staffUserId,
                staffUserId
        );
        return ApiResponse.success();
    }

    @PostMapping("/mobile/staff/workorders/{id}/records")
    @Transactional
    public ApiResponse<Map<String, Object>> addWorkOrderRecord(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        require(principal, "workorder.staff.start");
        Long staffUserId = staff(principal);
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM work_order
                WHERE id = ? AND assignee_id = ? AND work_status = 'PROCESSING' AND deleted = 0
                """,
                Long.class,
                id,
                staffUserId
        );
        if (count == null || count == 0) {
            throw new BusinessException(409, "只能为本人处理中工单追加处理记录");
        }
        String content = required(body, "请填写处理记录", "content", "result", "remark");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO work_order_record (
                        work_order_id, operator_id, action, content, operated_at,
                        status, created_by, updated_by, deleted
                    )
                    VALUES (?, ?, 'PROGRESS', ?, NOW(), 'ACTIVE', ?, ?, 0)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, id);
            statement.setLong(2, staffUserId);
            statement.setString(3, content);
            statement.setLong(4, staffUserId);
            statement.setLong(5, staffUserId);
            return statement;
        }, keyHolder);
        Long recordId = keyHolder.getKey().longValue();
        List<Long> fileIds = ids(body, "attachmentFileIds", "fileIds");
        fileService.claimDraftFiles(fileIds, "WORK_ORDER", id, staffUserId);
        for (Long fileId : fileIds) {
            jdbcTemplate.update(
                    """
                    INSERT INTO work_order_attachment (
                        work_order_id, work_order_record_id, file_id,
                        attachment_type, status, created_by, updated_by, deleted
                    )
                    VALUES (?, ?, ?, 'PROCESS_IMAGE', 'ENABLED', ?, ?, 0)
                    """,
                    id,
                    recordId,
                    fileId,
                    staffUserId,
                    staffUserId
            );
        }
        if (body.containsKey("customFields")) {
            fileService.claimDraftFiles(customFieldFileIds(body.get("customFields")), "WORK_ORDER", id, staffUserId);
            customFieldValueService.save("workorders", id, body.get("customFields"), staffUserId);
        }
        return ApiResponse.success(Map.of("id", recordId));
    }

    private void attachInspectionFiles(Long inspectionId, Long staffUserId, List<Long> fileIds) {
        if (fileIds.isEmpty()) return;
        fileService.claimDraftFiles(fileIds, "INSPECTION", inspectionId, staffUserId);
        int order = 0;
        for (Long fileId : fileIds) {
            jdbcTemplate.update(
                    """
                    INSERT INTO inspection_attachment (
                        inspection_id, file_id, sort_order,
                        created_by, updated_by, deleted
                    )
                    VALUES (?, ?, ?, ?, ?, 0)
                    """,
                    inspectionId,
                    fileId,
                    order++,
                    staffUserId,
                    staffUserId
            );
        }
    }

    private List<Map<String, Object>> inspectionAttachments(Long inspectionId) {
        return jdbcTemplate.queryForList(
                """
                SELECT file.id, file.original_name AS name,
                       CONCAT('/api/mobile/files/', file.id) AS url,
                       file.mime_type AS mimeType, file.file_size AS fileSize
                FROM inspection_attachment attachment
                INNER JOIN file_upload file
                        ON file.id = attachment.file_id
                       AND file.deleted = 0
                       AND file.status = 'ENABLED'
                WHERE attachment.inspection_id = ? AND attachment.deleted = 0
                  AND file.biz_type = 'INSPECTION' AND file.biz_id = ?
                  AND file.status = 'ENABLED' AND file.deleted = 0
                ORDER BY attachment.sort_order, attachment.id
                """,
                inspectionId,
                inspectionId
        );
    }

    private Map<Long, List<Map<String, Object>>> inspectionHistoryAttachments(Long inspectionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT attachment.inspection_history_id AS historyId,
                       file.id, file.original_name AS name,
                       CONCAT('/api/mobile/files/', file.id) AS url,
                       file.mime_type AS mimeType, file.file_size AS fileSize
                FROM inspection_history_attachment attachment
                INNER JOIN inspection_record_history history
                        ON history.id = attachment.inspection_history_id
                       AND history.deleted = 0
                INNER JOIN file_upload file
                        ON file.id = attachment.file_id
                       AND file.deleted = 0
                       AND file.status = 'ENABLED'
                WHERE history.inspection_id = ? AND attachment.deleted = 0
                  AND file.biz_type = 'INSPECTION' AND file.biz_id = ?
                ORDER BY attachment.inspection_history_id, attachment.sort_order, attachment.id
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

    private void attachInspectionHistoryFiles(
            Long historyId,
            Long inspectionId,
            Long staffUserId,
            List<Long> fileIds
    ) {
        if (fileIds.isEmpty()) return;
        fileService.claimDraftFiles(fileIds, "INSPECTION", inspectionId, staffUserId);
        int order = 0;
        for (Long fileId : fileIds) {
            jdbcTemplate.update(
                    """
                    INSERT INTO inspection_history_attachment (
                        inspection_history_id, file_id, sort_order,
                        created_by, updated_by, deleted
                    )
                    VALUES (?, ?, ?, ?, ?, 0)
                    """,
                    historyId,
                    fileId,
                    order++,
                    staffUserId,
                    staffUserId
            );
        }
    }

    private Long inspectionHistory(Long id, String action, String content, Long staffUserId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO inspection_record_history (
                    inspection_id, action, content, operator_id,
                    status, created_by, updated_by, deleted
                )
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0)
                """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, id);
            statement.setString(2, action);
            statement.setString(3, content);
            statement.setLong(4, staffUserId);
            statement.setLong(5, staffUserId);
            statement.setLong(6, staffUserId);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private String normalizedInspectionResult(Map<String, Object> body) {
        String result = required(body, "请选择巡检结果", "inspectionResult", "result")
                .toUpperCase(Locale.ROOT);
        if (!INSPECTION_RESULTS.contains(result)) throw new BusinessException(400, "巡检结果无效");
        return result;
    }

    private String energyReadingSelect() {
        return """
                SELECT reading.id, reading.code,
                       meter.meter_no AS meterNo, meter.meter_name AS meterName,
                       meter.meter_type AS meterType,
                       tenant.name AS tenantName,
                       reading.period_month AS periodMonth,
                       reading.reading_date AS readingDate,
                       reading.previous_reading AS previousReading,
                       reading.current_reading AS currentReading,
                       reading.usage_amount AS usageAmount,
                       reading.unit_price AS unitPrice,
                       reading.settlement_amount AS settlementAmount,
                       reading.operator_name AS operatorName,
                       reading.abnormal_flag AS abnormal,
                       reading.abnormal_reason AS abnormalReason,
                       reading.abnormal_status AS abnormalStatus,
                       reading.bill_status AS billStatus,
                       CASE WHEN reading.attachment_file_id IS NULL THEN NULL
                            ELSE CONCAT('/api/mobile/files/', reading.attachment_file_id) END AS attachmentUrl,
                       reading.remark, reading.created_at AS createdAt
                FROM energy_reading reading
                JOIN energy_meter meter ON meter.id = reading.meter_id AND meter.deleted = 0
                LEFT JOIN energy_meter_binding binding
                  ON binding.id = (
                      SELECT candidate.id
                      FROM energy_meter_binding candidate
                      WHERE candidate.meter_id = meter.id
                        AND candidate.deleted = 0
                        AND candidate.effective_from <= reading.reading_date
                        AND (candidate.effective_to IS NULL OR candidate.effective_to >= reading.reading_date)
                      ORDER BY candidate.effective_from DESC, candidate.id DESC
                      LIMIT 1
                  )
                LEFT JOIN tenant ON tenant.id = binding.tenant_id AND tenant.deleted = 0
                """;
    }

    private String energyReadingCount() {
        return """
                SELECT COUNT(*)
                FROM energy_reading reading
                JOIN energy_meter meter ON meter.id = reading.meter_id AND meter.deleted = 0
                LEFT JOIN energy_meter_binding binding
                  ON binding.id = (
                      SELECT candidate.id
                      FROM energy_meter_binding candidate
                      WHERE candidate.meter_id = meter.id
                        AND candidate.deleted = 0
                        AND candidate.effective_from <= reading.reading_date
                        AND (candidate.effective_to IS NULL OR candidate.effective_to >= reading.reading_date)
                      ORDER BY candidate.effective_from DESC, candidate.id DESC
                      LIMIT 1
                  )
                """;
    }

    private void requireMeterBinding(Long meterId, LocalDate readingDate) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM energy_meter_binding
                WHERE meter_id = ? AND deleted = 0
                  AND status IN ('ACTIVE','ENDED')
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                """,
                Long.class,
                meterId,
                Date.valueOf(readingDate),
                Date.valueOf(readingDate)
        );
        if (count == null || count == 0) throw new BusinessException(409, "表具未绑定有效计量主体");
        if (count > 1) throw new BusinessException(409, "表具计量主体绑定期间重叠");
    }

    private BigDecimal previousReading(Long meterId, String month, BigDecimal initial) {
        List<BigDecimal> values = jdbcTemplate.queryForList(
                """
                SELECT current_reading
                FROM energy_reading
                WHERE meter_id = ? AND period_month < ? AND deleted = 0
                ORDER BY period_month DESC, reading_date DESC, id DESC
                LIMIT 1
                """,
                BigDecimal.class,
                meterId,
                month
        );
        return values.isEmpty() ? initial : values.getFirst();
    }

    private BigDecimal previousUsage(Long meterId, String month) {
        List<BigDecimal> values = jdbcTemplate.queryForList(
                """
                SELECT usage_amount
                FROM energy_reading
                WHERE meter_id = ? AND period_month < ? AND deleted = 0
                ORDER BY period_month DESC, reading_date DESC, id DESC
                LIMIT 1
                """,
                BigDecimal.class,
                meterId,
                month
        );
        return values.isEmpty() ? BigDecimal.ZERO : values.getFirst();
    }

    private BigDecimal unitPrice(String periodMonth, String meterType) {
        List<BigDecimal> prices = jdbcTemplate.queryForList(
                """
                SELECT unit_price
                FROM energy_price_setting
                WHERE period_month = ? AND meter_type = ? AND deleted = 0
                LIMIT 1
                """,
                BigDecimal.class,
                periodMonth,
                meterType.toUpperCase(Locale.ROOT)
        );
        return prices.isEmpty() ? BigDecimal.ZERO : prices.getFirst().setScale(4, RoundingMode.HALF_UP);
    }

    private void auditEnergyReading(Long id, Long staffUserId) {
        Map<String, Object> snapshot = jdbcTemplate.queryForMap(
                """
                SELECT id, meter_id, period_month, reading_date,
                       previous_reading, current_reading, usage_amount,
                       unit_price, settlement_amount, abnormal_flag,
                       abnormal_reason, attachment_file_id
                FROM energy_reading
                WHERE id = ?
                """,
                id
        );
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO energy_reading_audit (
                        reading_id, action, before_snapshot, after_snapshot, reason, operator_id
                    )
                    VALUES (?, 'CREATE', NULL, ?, 'STAFF移动抄表', ?)
                    """,
                    id,
                    objectMapper.writeValueAsString(snapshot),
                    staffUserId
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("抄表审计序列化失败", exception);
        }
    }

    private String visitorSelect() {
        return """
                SELECT appointment.id, appointment.code AS invitationNumber,
                       appointment.visitor_name AS visitorName,
                       CONCAT(LEFT(appointment.visitor_phone, 3), '****', RIGHT(appointment.visitor_phone, 4)) AS maskedPhone,
                       appointment.car_plate_no AS carPlateNo,
                       appointment.visit_company AS visitCompany,
                       tenant.name AS tenantName,
                       contact.name AS applicantName,
                       appointment.visit_time AS visitTime,
                       appointment.visit_reason AS visitReason,
                       appointment.review_remark AS reviewRemark,
                       appointment.reviewed_at AS reviewedAt,
                       reviewer.real_name AS reviewerName,
                       appointment.qr_code_file_id AS qrCodeFileId,
                       qr_file.original_name AS qrCodeFileName,
                       appointment.qr_uploaded_at AS qrUploadedAt,
                       CASE WHEN appointment.qr_code_file_id IS NULL THEN NULL
                            ELSE CONCAT('/api/mobile/files/', appointment.qr_code_file_id)
                       END AS qrCodeUrl,
                       CASE
                         WHEN DATE(appointment.visit_time) < CURRENT_DATE
                          AND appointment.appointment_status IN ('PENDING','REGISTERED') THEN 'EXPIRED'
                         ELSE appointment.appointment_status
                       END AS status,
                       appointment.created_at AS createdAt
                FROM visitor_appointment appointment
                LEFT JOIN tenant ON tenant.id = appointment.tenant_id AND tenant.deleted = 0
                LEFT JOIN mobile_user_bind bind_record
                  ON bind_record.mobile_user_id = appointment.applicant_mobile_user_id
                 AND bind_record.bind_type = 'TENANT'
                 AND bind_record.status = 'ENABLED'
                 AND bind_record.deleted = 0
                LEFT JOIN tenant_contact contact
                  ON contact.id = bind_record.tenant_contact_id AND contact.deleted = 0
                LEFT JOIN sys_user reviewer
                  ON reviewer.id = appointment.reviewed_by AND reviewer.deleted = 0
                LEFT JOIN file_upload qr_file
                  ON qr_file.id = appointment.qr_code_file_id
                 AND qr_file.biz_type = 'VISITOR'
                 AND qr_file.access_scope = 'INTERNAL'
                 AND qr_file.status = 'ENABLED'
                 AND qr_file.deleted = 0
                """;
    }

    private List<Map<String, Object>> lockVisitor(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT appointment_status AS status,
                       CASE WHEN DATE(visit_time) < CURRENT_DATE THEN TRUE ELSE FALSE END AS expired
                FROM visitor_appointment
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "访客预约不存在");
        return rows;
    }

    private void visitorPass(Long id, String type, Long staffUserId) {
        Long existing = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM visitor_pass_record
                WHERE appointment_id = ? AND pass_type = ? AND deleted = 0
                """,
                Long.class,
                id,
                type
        );
        if (existing != null && existing > 0) return;
        jdbcTemplate.update(
                """
                INSERT INTO visitor_pass_record (
                    appointment_id, pass_type, pass_time, gate_name, operator_id,
                    status, created_by, updated_by, deleted
                )
                VALUES (?, ?, NOW(), 'STAFF移动核验', ?, 'ACTIVE', ?, ?, 0)
                """,
                id,
                type,
                staffUserId,
                staffUserId,
                staffUserId
        );
    }

    private String investmentLeadSelect() {
        return """
                SELECT lead_record.id, lead_record.code,
                       lead_record.name AS applicantName,
                       CASE WHEN mobile.phone IS NULL OR mobile.phone = '' THEN ''
                            ELSE CONCAT(LEFT(mobile.phone, 3), '****', RIGHT(mobile.phone, 4)) END AS verifiedPhone,
                       lead_record.company_name AS companyName,
                       lead_record.listing_title_snapshot AS listingTitleSnapshot,
                       lead_record.building_name_snapshot AS buildingNameSnapshot,
                       lead_record.floor_snapshot AS floorSnapshot,
                       lead_record.room_code_snapshot AS roomCodeSnapshot,
                       lead_record.area_snapshot AS areaSnapshot,
                       lead_record.preferred_visit_time AS preferredVisitTime,
                       lead_record.lead_status AS status,
                       lead_record.created_at AS createdAt,
                       lead_record.remark
                FROM investment_lead lead_record
                JOIN mobile_user mobile
                  ON mobile.id = lead_record.mobile_user_id AND mobile.deleted = 0
                """;
    }

    private void requireMobileLead(Long id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM investment_lead WHERE id = ? AND mobile_user_id IS NOT NULL AND deleted = 0",
                Long.class,
                id
        );
        if (count == null || count == 0) throw new BusinessException(404, "看房预约不存在");
    }

    private String normalizeLeadStatus(Object value) {
        String status = value == null ? "NEW" : String.valueOf(value).toUpperCase(Locale.ROOT);
        return switch (status) {
            case "CONTACTED", "NEGOTIATING" -> "FOLLOWING";
            case "CONVERTED_TENANT", "CONVERTED_CONTRACT" -> "CONVERTED";
            default -> LEAD_STATUSES.contains(status) ? status : "NEW";
        };
    }

    private Set<String> leadTransitions(String current) {
        return switch (current) {
            case "NEW" -> Set.of("FOLLOWING", "VIEWING_SCHEDULED", "INVALID");
            case "FOLLOWING" -> Set.of("VIEWING_SCHEDULED", "VIEWED", "CONVERTED", "INVALID");
            case "VIEWING_SCHEDULED" -> Set.of("FOLLOWING", "VIEWED", "INVALID");
            case "VIEWED" -> Set.of("FOLLOWING", "CONVERTED", "INVALID");
            default -> Set.of();
        };
    }

    private Long staff(AuthPrincipal principal) {
        return MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
    }

    private VisitorApprovalService visitorApprovalService() {
        if (visitorApprovalService == null) {
            throw new BusinessException(500, "访客审核服务不可用");
        }
        return visitorApprovalService;
    }

    private String staffName(Long staffUserId) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(real_name, ''), username) FROM sys_user WHERE id = ? AND deleted = 0",
                String.class,
                staffUserId
        );
        if (values.isEmpty()) throw new BusinessException(403, "物业员工档案已失效");
        return values.getFirst();
    }

    private List<Map<String, Object>> inspectionInspectorOptions() {
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT staff.code AS value,
                       COALESCE(NULLIF(staff.real_name, ''), staff.username) AS label
                FROM sys_user staff
                WHERE staff.status IN ('ACTIVE', 'ENABLED')
                  AND staff.mobile_enabled = 1
                  AND staff.deleted = 0
                  AND EXISTS (
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
                      AND permission_record.code IN (
                        'staff-mobile:inspection:write',
                        'staff-mobile:inspection:*',
                        'staff-mobile:*',
                        '*'
                      )
                  )
                ORDER BY label, value
                """
        );
    }

    private Map<String, Object> resolveInspectionInspector(String inspectorCode, Long currentStaffUserId) {
        String normalizedCode = text(inspectorCode);
        String selector = normalizedCode == null ? " staff.id = ?" : " staff.code = ?";
        Object selectorValue = normalizedCode == null ? currentStaffUserId : normalizedCode;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT staff.id AS sysUserId,
                       staff.code AS inspectorCode,
                       COALESCE(NULLIF(staff.real_name, ''), staff.username) AS inspectorName
                FROM sys_user staff
                WHERE """ + selector + """
                  AND staff.status IN ('ACTIVE', 'ENABLED')
                  AND staff.mobile_enabled = 1
                  AND staff.deleted = 0
                  AND EXISTS (
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
                      AND permission_record.code IN (
                        'staff-mobile:inspection:write',
                        'staff-mobile:inspection:*',
                        'staff-mobile:*',
                        '*'
                      )
                  )
                LIMIT 1
                """,
                selectorValue
        );
        if (rows.isEmpty()) throw new BusinessException(400, "所选巡检人未启用或无巡检填报权限");
        return rows.getFirst();
    }

    private List<Map<String, Object>> inspectionAreaOptions() {
        return jdbcTemplate.queryForList(
                """
                SELECT option_value AS value, option_label AS label, option_scope AS scope
                FROM (
                    SELECT building.name AS option_value,
                           building.name AS option_label,
                           'BUILDING' AS option_scope,
                           building.id AS building_sort,
                           0 AS floor_sort,
                           '' AS room_sort,
                           1 AS level_sort
                    FROM building
                    WHERE building.deleted = 0
                      AND building.status IN ('ACTIVE', 'ENABLED')

                    UNION ALL

                    SELECT CONCAT(building.name, ' / ', floor.name) AS option_value,
                           CONCAT(building.name, ' / ', floor.name) AS option_label,
                           'FLOOR' AS option_scope,
                           building.id AS building_sort,
                           floor.floor_no AS floor_sort,
                           '' AS room_sort,
                           2 AS level_sort
                    FROM building_floor floor
                    INNER JOIN building
                      ON building.id = floor.building_id
                     AND building.deleted = 0
                     AND building.status IN ('ACTIVE', 'ENABLED')
                    WHERE floor.deleted = 0
                      AND floor.status IN ('ACTIVE', 'ENABLED')

                    UNION ALL

                    SELECT CONCAT(building.name, ' / ', floor.name, ' / ', room.room_no) AS option_value,
                           CONCAT(building.name, ' / ', floor.name, ' / ', room.room_no) AS option_label,
                           'ROOM' AS option_scope,
                           building.id AS building_sort,
                           floor.floor_no AS floor_sort,
                           room.room_no AS room_sort,
                           3 AS level_sort
                    FROM building_room room
                    INNER JOIN building
                      ON building.id = room.building_id
                     AND building.deleted = 0
                     AND building.status IN ('ACTIVE', 'ENABLED')
                    INNER JOIN building_floor floor
                      ON floor.id = room.floor_id
                     AND floor.deleted = 0
                     AND floor.status IN ('ACTIVE', 'ENABLED')
                    WHERE room.deleted = 0
                      AND room.status IN ('ACTIVE', 'ENABLED')
                ) options
                ORDER BY building_sort, floor_sort, level_sort, room_sort
                """
        );
    }

    private void validateInspectionArea(String area) {
        boolean known = inspectionAreaOptions().stream()
                .anyMatch(option -> area.equals(String.valueOf(option.get("value"))));
        if (!known) throw new BusinessException(400, "请选择后台楼宇空间中已启用的巡检区域");
    }

    private void require(AuthPrincipal principal, String capability) {
        MobileApiSupport.requireCapability(principal, capability);
    }

    private boolean has(AuthPrincipal principal, String capability) {
        return principal != null
                && principal.permissions() != null
                && principal.permissions().contains(capability);
    }

    private void appendEquals(StringBuilder where, List<Object> args, String column, String value) {
        String normalized = text(value);
        if (normalized == null) return;
        where.append(" AND ").append(column).append(" = ?");
        args.add(normalized.toUpperCase(Locale.ROOT));
    }

    private void appendLong(StringBuilder where, List<Object> args, String column, Long value) {
        if (value == null) return;
        where.append(" AND ").append(column).append(" = ?");
        args.add(value);
    }

    private String required(Map<String, Object> body, String message, String... keys) {
        String value = textValue(body, keys);
        if (value == null) throw new BusinessException(400, message);
        return value;
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

    private String textValue(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            String value = text(body.get(key));
            if (value != null) return value;
        }
        return null;
    }

    private String text(Object value) {
        if (value == null) return null;
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.intValue() != 0;
        return value != null && ("true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value)));
    }

    private LocalDate dateValue(Map<String, Object> body, String key, LocalDate fallback) {
        String value = textValue(body, key);
        if (value == null) return fallback;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, "日期格式必须为YYYY-MM-DD");
        }
    }

    private String month(String value) {
        String normalized = text(value);
        if (normalized == null) return YearMonth.now().toString();
        try {
            return YearMonth.parse(normalized).toString();
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, "月份格式必须为YYYY-MM");
        }
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        if (value == null || String.valueOf(value).isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "读数格式不正确");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> ids(Map<String, Object> body, String... keys) {
        if (body == null) return List.of();
        Object raw = null;
        for (String key : keys) {
            if (body.containsKey(key)) {
                raw = body.get(key);
                break;
            }
        }
        if (raw == null) return List.of();
        List<?> values = raw instanceof List<?> list ? list : List.of(raw);
        return values.stream().map(value -> {
            if (value instanceof Number number) return number.longValue();
            if (value instanceof Map<?, ?> map) {
                Object fileId = map.get("fileId");
                if (fileId == null) fileId = map.get("id");
                return fileId instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(fileId));
            }
            return Long.valueOf(String.valueOf(value));
        }).filter(id -> id > 0).distinct().toList();
    }

    private List<Long> customFieldFileIds(Object value) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        collectCustomFieldFileIds(value, false, result);
        return List.copyOf(result);
    }

    private List<Long> inspectionFileIds(Map<String, Object> body) {
        LinkedHashSet<Long> result = new LinkedHashSet<>(ids(body, "attachmentFileIds", "fileIds"));
        result.addAll(customFieldFileIds(body.get("images")));
        return List.copyOf(result);
    }

    private List<Long> inspectionResolutionFileIds(Map<String, Object> body) {
        if (body == null) return List.of();
        LinkedHashSet<Long> result = new LinkedHashSet<>(ids(body, "resolutionFileIds"));
        result.addAll(customFieldFileIds(body.get("resolutionImages")));
        return List.copyOf(result);
    }

    private void collectCustomFieldFileIds(Object value, boolean acceptNumeric, Set<Long> target) {
        if (value == null) return;
        if (value instanceof Map<?, ?> map) {
            Object direct = map.get("fileId") != null ? map.get("fileId") : map.get("id");
            Long directId = positiveLong(direct);
            if (directId != null) target.add(directId);
            if (map.get("files") != null) {
                collectCustomFieldFileIds(map.get("files"), true, target);
            } else if (directId == null) {
                map.values().forEach(item -> collectCustomFieldFileIds(item, false, target));
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> collectCustomFieldFileIds(item, true, target));
            return;
        }
        if (acceptNumeric) {
            Long id = positiveLong(value);
            if (id != null) target.add(id);
        }
    }

    private Long positiveLong(Object value) {
        if (value == null) return null;
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

package com.dehui.property.modules.parking.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ParkingController {

    private static final BigDecimal MAX_PARKING_BILL_AMOUNT = new BigDecimal("9999999999.99");
    private static final BigDecimal MAX_PARKING_MONTHLY_FEE = new BigDecimal("99999999.99");
    private static final String ACTIVE_BILLING_MONTH_UNIQUE_KEY = "uk_parking_bill_active_month";
    private static final Map<String, String> PARKING_SPACE_ORDER_FIELDS = Map.ofEntries(
            Map.entry("spaceNo", "s.space_no"),
            Map.entry("area", "s.area"),
            Map.entry("floor", "s.location"),
            Map.entry("type", "s.space_type"),
            Map.entry("monthlyFee", "a.monthly_fee"),
            Map.entry("partyNameSnapshot", "t.name"),
            Map.entry("plateNo", "a.car_plate_no"),
            Map.entry("status", "s.rent_status")
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;

    public ParkingController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    @GetMapping("/parking/spaces")
    public ApiResponse<PageResponse<Map<String, Object>>> spaces(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String spaceNo,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String partyName,
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) BigDecimal monthlyFee,
            @RequestParam(required = false) BigDecimal minMonthlyFee,
            @RequestParam(required = false) BigDecimal maxMonthlyFee,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        String selectSql = """
                SELECT s.id, s.code, s.space_no AS spaceNo, s.area AS area, s.location AS floor, s.location,
                       s.space_type AS type, s.space_type AS spaceType, COALESCE(pt.label, s.space_type) AS typeText,
                       s.monthly_rate AS monthlyRate,
                       s.rent_status AS status, s.remark,
                       t.name AS partyNameSnapshot, a.car_plate_no AS plateNo,
                       a.id AS assignmentId,
                       a.monthly_fee AS monthlyFee,
                       a.unit_price_snapshot AS unitPriceSnapshot,
                       a.start_date AS effectiveFrom,
                       a.end_date AS effectiveTo
                FROM parking_space s
                LEFT JOIN parking_assignment a ON a.parking_space_id = s.id
                                                  AND a.deleted = 0
                                                  AND a.status IN ('ACTIVE', 'ENABLED')
                LEFT JOIN tenant t ON t.id = a.tenant_id
                LEFT JOIN parking_space_type_option pt ON pt.code = s.space_type AND pt.deleted = 0 AND pt.status = 'ENABLED'
                WHERE s.deleted = 0
                """;
        String countSql = """
                SELECT COUNT(DISTINCT s.id)
                FROM parking_space s
                LEFT JOIN parking_assignment a ON a.parking_space_id = s.id
                                                  AND a.deleted = 0
                                                  AND a.status IN ('ACTIVE', 'ENABLED')
                LEFT JOIN tenant t ON t.id = a.tenant_id
                WHERE s.deleted = 0
                """;
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendParkingSpaceFilters(
                where, args, keyword, status, area, spaceNo, floor, type, partyName, plateNo,
                monthlyFee, minMonthlyFee, maxMonthlyFee
        );
        CompiledQuery customQuery = compileSpaceCustomQuery(customFilters, groupBy, sorts, sortField, sortDirection);
        if (customQuery != null && customQuery.hasPredicates()) {
            where.append(" AND ").append(customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(
                        PARKING_SPACE_ORDER_FIELDS, groupBy, sorts, sortField, sortDirection,
                        "s.space_no", "s.id"
                )
                : String.join(", ", customQuery.orderExpressions());
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                selectSql + where + " ORDER BY " + orderBy,
                countSql + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("parking-spaces", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> spaces(
            String keyword,
            String status,
            String area,
            String spaceNo,
            String floor,
            String type,
            String partyName,
            String plateNo,
            BigDecimal monthlyFee,
            BigDecimal minMonthlyFee,
            BigDecimal maxMonthlyFee,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return spaces(
                keyword, status, area, spaceNo, floor, type, partyName, plateNo,
                monthlyFee, minMonthlyFee, maxMonthlyFee, null,
                sortField, sortDirection, sorts, groupBy, page, pageSize
        );
    }

    @GetMapping("/parking/spaces/summary")
    public ApiResponse<Map<String, Object>> spaceSummary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String spaceNo,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String partyName,
            @RequestParam(required = false) String plateNo,
            @RequestParam(required = false) BigDecimal monthlyFee,
            @RequestParam(required = false) BigDecimal minMonthlyFee,
            @RequestParam(required = false) BigDecimal maxMonthlyFee,
            @RequestParam(required = false) String customFilters) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendParkingSpaceFilters(
                where, args, keyword, status, area, spaceNo, floor, type, partyName, plateNo,
                monthlyFee, minMonthlyFee, maxMonthlyFee
        );
        CompiledQuery customQuery = compileSpaceCustomQuery(customFilters, null, null, null, null);
        if (customQuery != null && customQuery.hasPredicates()) {
            where.append(" AND ").append(customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }
        return ApiResponse.success(jdbcTemplate.queryForMap("""
                SELECT COUNT(DISTINCT s.id) AS totalCount,
                       COUNT(DISTINCT CASE WHEN s.rent_status = 'AVAILABLE' THEN s.id END) AS availableCount,
                       COUNT(DISTINCT CASE WHEN s.rent_status = 'OCCUPIED' THEN s.id END) AS occupiedCount,
                       COUNT(DISTINCT CASE WHEN s.rent_status IN ('MAINTENANCE', 'DISABLED', 'RESERVED') THEN s.id END) AS unavailableCount
                FROM parking_space s
                LEFT JOIN parking_assignment a ON a.parking_space_id = s.id
                                                  AND a.deleted = 0
                                                  AND a.status IN ('ACTIVE', 'ENABLED')
                LEFT JOIN tenant t ON t.id = a.tenant_id
                WHERE s.deleted = 0
                """ + where, args.toArray()));
    }

    public ApiResponse<Map<String, Object>> spaceSummary(
            String keyword,
            String status,
            String area,
            String spaceNo,
            String floor,
            String type,
            String partyName,
            String plateNo,
            BigDecimal monthlyFee,
            BigDecimal minMonthlyFee,
            BigDecimal maxMonthlyFee) {
        return spaceSummary(
                keyword, status, area, spaceNo, floor, type, partyName, plateNo,
                monthlyFee, minMonthlyFee, maxMonthlyFee, null
        );
    }

    private CompiledQuery compileSpaceCustomQuery(
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
                        PARKING_SPACE_ORDER_FIELDS,
                        sortField,
                        sortDirection,
                        "s.space_no",
                        "s.id"
                );
        return customFieldQueryService.compile(
                "parking-spaces",
                customFilters,
                groupBy,
                sorts,
                orderPolicy
        );
    }

    @GetMapping("/parking/spaces/type-options")
    public ApiResponse<List<Map<String, Object>>> spaceTypes() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT label, code AS value
                FROM parking_space_type_option
                WHERE deleted = 0 AND status = 'ENABLED'
                ORDER BY sort_order ASC, id ASC
                """));
    }

    @PostMapping("/parking/spaces/type-options")
    public ApiResponse<Void> createSpaceType(@RequestBody Map<String, Object> body) {
        String label = JdbcMaps.requiredStr(body, "Space type label is required", "label", "name");
        String code = normalizeSpaceTypeCode(JdbcMaps.str(body, "value", "code"));
        if (code == null || code.isBlank()) {
            code = normalizeSpaceTypeCode(label);
        }
        jdbcTemplate.update("""
                INSERT INTO parking_space_type_option (code, label, sort_order, status, created_by, updated_by)
                VALUES (?, ?, 999, 'ENABLED', ?, ?)
                ON DUPLICATE KEY UPDATE label = VALUES(label), status = 'ENABLED', updated_by = VALUES(updated_by)
                """, code, label, 0L, 0L);
        return ApiResponse.success();
    }

    @PostMapping("/parking/spaces")
    public ApiResponse<Void> createSpace(@RequestBody Map<String, Object> body) {
        saveSpace(null, body);
        return ApiResponse.success();
    }

    @PutMapping("/parking/spaces/{id}")
    public ApiResponse<Void> updateSpace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        saveSpace(id, body);
        return ApiResponse.success();
    }

    @DeleteMapping("/parking/spaces/{id}")
    public ApiResponse<Void> deleteSpace(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update(
                "UPDATE parking_space SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                id
        ), "Parking space not found");
        return ApiResponse.success();
    }

    @PatchMapping("/parking/spaces/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        ensureUpdated(jdbcTemplate.update(
                "UPDATE parking_space SET rent_status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                status,
                0L,
                id
        ), "Parking space not found");
        return ApiResponse.success();
    }

    @PostMapping("/parking/spaces/{id}/bind")
    @Transactional
    public ApiResponse<Void> bind(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tenantId = JdbcMaps.longVal(body, "partyId", "tenantId");
        if (tenantId == null) {
            throw new BusinessException(400, "Tenant is required before binding parking space");
        }
        requireActiveReference(
                "SELECT COUNT(*) FROM tenant WHERE id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')",
                "Tenant does not exist or is disabled",
                tenantId
        );
        List<Map<String, Object>> spaces = jdbcTemplate.queryForList(
                "SELECT id, monthly_rate FROM parking_space WHERE id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED') FOR UPDATE",
                id
        );
        if (spaces.isEmpty()) {
            throw new BusinessException(404, "Parking space not found or disabled");
        }
        LocalDate startDate = LocalDate.parse(JdbcMaps.date(body, JdbcMaps.today(), "startDate", "effectiveFrom"));
        String endDateValue = JdbcMaps.str(body, "endDate", "effectiveTo");
        LocalDate endDate = endDateValue == null ? null : LocalDate.parse(endDateValue);
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(400, "Parking binding end date cannot be before its start date");
        }
        Long overlaps = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM parking_assignment
                WHERE parking_space_id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED', 'RELEASED')
                  AND start_date <= ?
                  AND (end_date IS NULL OR end_date >= ?)
                """,
                Long.class,
                id,
                endDate == null ? LocalDate.of(9999, 12, 31) : endDate,
                startDate
        );
        if (overlaps != null && overlaps > 0) {
            throw new BusinessException(409, "Parking space already has an overlapping binding");
        }
        Long tenantContactId = JdbcMaps.longVal(body, "tenantContactId");
        Long mobileUserId = null;
        if (tenantContactId != null) {
            requireActiveReference(
                    "SELECT COUNT(*) FROM tenant_contact WHERE id = ? AND tenant_id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')",
                    "Tenant contact does not belong to the selected tenant",
                    tenantContactId,
                    tenantId
            );
            List<Map<String, Object>> mobileBindings = jdbcTemplate.queryForList(
                    """
                    SELECT mobile_user_id AS mobileUserId
                    FROM mobile_user_bind
                    WHERE tenant_contact_id = ?
                      AND tenant_id = ?
                      AND bind_type = 'TENANT'
                      AND deleted = 0
                      AND status IN ('ACTIVE', 'ENABLED')
                    ORDER BY id DESC
                    LIMIT 1
                    """,
                    tenantContactId,
                    tenantId
            );
            mobileUserId = mobileBindings.isEmpty() ? null : longValue(mobileBindings.getFirst().get("mobileUserId"));
        }
        BigDecimal monthlyFee = JdbcMaps.decimal(body, null, "monthlyFee", "unitPriceSnapshot");
        if (monthlyFee == null) {
            monthlyFee = decimalValue(spaces.getFirst().get("monthly_rate"));
        }
        monthlyFee = validateParkingMonthlyFee(monthlyFee);
        jdbcTemplate.update(
                """
                INSERT INTO parking_assignment
                    (code, parking_space_id, tenant_id, tenant_contact_id, mobile_user_id,
                     car_plate_no, vehicle_type, start_date, end_date, monthly_fee,
                     unit_price_snapshot, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("PAS"),
                id,
                tenantId,
                tenantContactId,
                mobileUserId,
                JdbcMaps.strOr(body, "", "plateNo", "carPlateNo"),
                JdbcMaps.strOr(body, "CAR", "vehicleType"),
                startDate,
                endDate,
                monthlyFee,
                monthlyFee,
                0L,
                0L,
                JdbcMaps.str(body, "remark")
        );
        jdbcTemplate.update("UPDATE parking_space SET rent_status = 'OCCUPIED', updated_by = ? WHERE id = ?", 0L, id);
        return ApiResponse.success();
    }

    @PatchMapping("/parking/assignments/{id}/monthly-fee")
    public ApiResponse<Void> updateAssignmentMonthlyFee(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        throw new BusinessException(409, "Parking price snapshots cannot be overwritten; release and create a new effective binding");
    }

    @PostMapping("/parking/spaces/{id}/release")
    @Transactional
    public ApiResponse<Void> release(@PathVariable Long id) {
        int released = jdbcTemplate.update(
                """
                UPDATE parking_assignment
                SET status = 'RELEASED',
                    end_date = COALESCE(end_date, ?),
                    updated_by = ?
                WHERE parking_space_id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                """,
                JdbcMaps.today(),
                0L,
                id
        );
        if (released == 0) {
            throw new BusinessException(404, "Active parking assignment not found");
        }
        ensureUpdated(jdbcTemplate.update(
                "UPDATE parking_space SET rent_status = 'AVAILABLE', updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                id
        ), "Parking space not found");
        return ApiResponse.success();
    }

    @GetMapping("/parking/assignments")
    public ApiResponse<List<Map<String, Object>>> assignments() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT pa.*,
                       pa.parking_space_id AS parkingSpaceId,
                       pa.tenant_id AS tenantId,
                       pa.tenant_contact_id AS tenantContactId,
                       pa.mobile_user_id AS mobileUserId,
                       pa.car_plate_no AS plateNo,
                       pa.vehicle_type AS vehicleType,
                       pa.start_date AS startDate,
                       pa.end_date AS endDate,
                       pa.monthly_fee AS monthlyFee,
                       pa.unit_price_snapshot AS unitPriceSnapshot,
                       ps.space_no AS spaceNo,
                       ps.area,
                       ps.location,
                       t.name AS tenantName,
                       tc.name AS contactName,
                       tc.phone AS contactPhone
                FROM parking_assignment pa
                LEFT JOIN parking_space ps ON ps.id = pa.parking_space_id AND ps.deleted = 0
                LEFT JOIN tenant t ON t.id = pa.tenant_id AND t.deleted = 0
                LEFT JOIN tenant_contact tc ON tc.id = pa.tenant_contact_id AND tc.deleted = 0
                WHERE pa.deleted = 0
                ORDER BY pa.id DESC
                """));
    }

    @GetMapping("/parking/bills")
    public ApiResponse<List<Map<String, Object>>> bills() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT pb.id, pb.code, pb.code AS billNo, pb.assignment_id AS assignmentId,
                       pb.tenant_id AS tenantId, pb.amount, pb.billing_month AS billingMonth,
                       pb.due_date AS dueDate,
                       pb.pay_status AS payStatus, pb.status, pb.remark,
                       pa.code AS assignmentCode, pa.car_plate_no AS plateNoSnapshot,
                       ps.space_no AS spaceNoSnapshot, t.name AS partyNameSnapshot,
                       '租户' AS partyTypeText, '月租' AS billTypeText,
                       pb.created_at AS createdTime
                FROM parking_bill pb
                LEFT JOIN parking_assignment pa ON pa.id = pb.assignment_id
                LEFT JOIN parking_space ps ON ps.id = pa.parking_space_id
                LEFT JOIN tenant t ON t.id = pb.tenant_id
                WHERE pb.deleted = 0
                ORDER BY pb.id DESC
                """));
    }

    @GetMapping("/parking/bills/assignments")
    public ApiResponse<List<Map<String, Object>>> billAssignments() {
        return ApiResponse.success(jdbcTemplate.queryForList("""
                SELECT pa.id AS assignmentId, pa.code AS assignmentCode,
                       pa.parking_space_id AS parkingSpaceId, ps.space_no AS spaceNo,
                       pa.tenant_id AS tenantId, t.name AS tenantName,
                       pa.monthly_fee AS monthlyFee,
                       pa.car_plate_no AS plateNo
                FROM parking_assignment pa
                JOIN parking_space ps ON ps.id = pa.parking_space_id AND ps.deleted = 0
                JOIN tenant t ON t.id = pa.tenant_id AND t.deleted = 0
                WHERE pa.deleted = 0 AND pa.status IN ('ACTIVE', 'ENABLED')
                ORDER BY ps.space_no ASC, pa.id DESC
                """));
    }

    @PostMapping("/parking/bills")
    public ApiResponse<Void> createBill(@RequestBody Map<String, Object> body) {
        Long assignmentId = requiredParkingAssignmentId(body);
        BigDecimal amount = requiredParkingBillAmount(body, "Parking bill amount must be greater than zero", "amount");
        String dueDate = requiredIsoDate(body, "dueDate");
        String billingMonth = requiredBillingMonth(body, dueDate);
        if (hasUnresolvedActiveParkingBill(assignmentId)) {
            throw new BusinessException(409, "Active parking bill has an unresolved billing month");
        }
        if (hasActiveParkingBill(assignmentId, billingMonth)) {
            throw new BusinessException(409, "Active parking bill already exists for this billing month");
        }
        int inserted = jdbcTemplate.update("""
                INSERT INTO parking_bill
                    (code, assignment_id, tenant_id, amount, billing_month, due_date,
                     pay_status, status, created_by, updated_by, remark)
                SELECT ?, pa.id, pa.tenant_id, ?, ?, ?, 'UNPAID', 'ACTIVE', ?, ?, ?
                FROM parking_assignment pa
                INNER JOIN parking_space ps
                        ON ps.id = pa.parking_space_id
                       AND ps.deleted = 0
                       AND ps.status IN ('ACTIVE', 'ENABLED')
                INNER JOIN tenant t
                        ON t.id = pa.tenant_id
                       AND t.deleted = 0
                       AND t.status IN ('ACTIVE', 'ENABLED')
                WHERE pa.id = ?
                  AND pa.deleted = 0
                  AND pa.status IN ('ACTIVE', 'ENABLED')
                """,
                JdbcMaps.code("PB"),
                amount,
                billingMonth,
                dueDate,
                0L,
                0L,
                JdbcMaps.str(body, "remark"),
                assignmentId
        );
        if (inserted == 0) {
            throw new BusinessException(400, "Active parking assignment not found");
        }
        return ApiResponse.success();
    }

    @Transactional
    @PostMapping("/parking/bills/sync")
    public ApiResponse<Map<String, Object>> syncBills(@RequestBody(required = false) Map<String, Object> body) {
        YearMonth billingPeriod = requiredSyncMonth(body);
        String billingMonth = billingPeriod.toString();
        LocalDate firstDay = billingPeriod.atDay(1);
        LocalDate lastDay = billingPeriod.atEndOfMonth();
        List<Map<String, Object>> assignments = jdbcTemplate.queryForList("""
                SELECT pa.id,
                       pa.tenant_id AS tenantId,
                       pa.parking_space_id AS parkingSpaceId,
                       pa.start_date AS startDate,
                       pa.end_date AS endDate,
                       pa.status,
                       pa.monthly_fee AS monthlyFee,
                       ps.id AS resolvedSpaceId,
                       t.id AS resolvedTenantId,
                       CASE WHEN EXISTS (
                           SELECT 1
                           FROM parking_bill pb
                           WHERE pb.assignment_id = pa.id
                             AND pb.billing_month = ?
                             AND pb.deleted = 0
                             AND pb.status IN ('ACTIVE', 'ENABLED')
                       ) THEN 1 ELSE 0 END AS hasActiveBill,
                       CASE WHEN EXISTS (
                           SELECT 1
                           FROM parking_bill legacy_pb
                           WHERE legacy_pb.assignment_id = pa.id
                             AND legacy_pb.billing_month IS NULL
                             AND legacy_pb.deleted = 0
                             AND legacy_pb.status IN ('ACTIVE', 'ENABLED')
                       ) THEN 1 ELSE 0 END AS hasUnresolvedActiveBill
                FROM parking_assignment pa
                LEFT JOIN parking_space ps
                       ON ps.id = pa.parking_space_id
                      AND ps.deleted = 0
                      AND ps.status IN ('ACTIVE', 'ENABLED')
                LEFT JOIN tenant t
                       ON t.id = pa.tenant_id
                      AND t.deleted = 0
                      AND t.status IN ('ACTIVE', 'ENABLED')
                WHERE pa.deleted = 0
                ORDER BY pa.id ASC
                """, billingMonth);
        int successCount = 0;
        int skippedCount = 0;
        int failureCount = 0;
        List<Map<String, Object>> issues = new ArrayList<>();

        for (Map<String, Object> assignment : assignments) {
            Long assignmentId = longValue(assignment.get("id"));
            if (!isActiveState(normalizedState(assignment.get("status")))) {
                skippedCount++;
                issues.add(syncIssue(assignmentId, "SKIPPED", "停车分配关系已失效"));
                continue;
            }
            if (longValue(assignment.get("resolvedSpaceId")) == null) {
                failureCount++;
                issues.add(syncIssue(assignmentId, "FAILURE", "关联停车位无效"));
                continue;
            }
            if (longValue(assignment.get("resolvedTenantId")) == null) {
                failureCount++;
                issues.add(syncIssue(assignmentId, "FAILURE", "关联租户无效"));
                continue;
            }

            BigDecimal monthlyFee = decimalValue(assignment.get("monthlyFee"));
            if (monthlyFee == null) {
                failureCount++;
                issues.add(syncIssue(assignmentId, "FAILURE", "未配置停车月费"));
                continue;
            }
            if (monthlyFee.compareTo(BigDecimal.ZERO) <= 0) {
                failureCount++;
                issues.add(syncIssue(assignmentId, "FAILURE", "停车月费必须大于 0"));
                continue;
            }

            LocalDate startDate = localDateValue(assignment.get("startDate"));
            LocalDate endDate = localDateValue(assignment.get("endDate"));
            if (startDate == null) {
                failureCount++;
                issues.add(syncIssue(assignmentId, "FAILURE", "停车分配有效期异常"));
                continue;
            }
            if (startDate.isAfter(firstDay)) {
                skippedCount++;
                issues.add(syncIssue(assignmentId, "SKIPPED", "分配关系月中生效，需要人工计费"));
                continue;
            }
            if (endDate != null && endDate.isBefore(lastDay)) {
                skippedCount++;
                issues.add(syncIssue(assignmentId, "SKIPPED", "分配关系月中终止，需要人工计费"));
                continue;
            }
            if (truthy(assignment.get("hasUnresolvedActiveBill"))) {
                failureCount++;
                issues.add(syncIssue(assignmentId, "FAILURE", "存在账期未确认的历史有效账单"));
                continue;
            }
            if (truthy(assignment.get("hasActiveBill"))) {
                skippedCount++;
                issues.add(syncIssue(assignmentId, "SKIPPED", "已存在该账期有效账单"));
                continue;
            }

            int inserted;
            try {
                inserted = jdbcTemplate.update(
                        """
                        INSERT INTO parking_bill
                            (code, assignment_id, tenant_id, amount, billing_month, due_date,
                             pay_status, status, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, 'UNPAID', 'ACTIVE', ?, ?)
                        """,
                        JdbcMaps.code("PB"),
                        assignmentId,
                        assignment.get("tenantId"),
                        monthlyFee,
                        billingMonth,
                        JdbcMaps.today(),
                        0L,
                        0L
                );
            } catch (DuplicateKeyException exception) {
                if (!isActiveBillingMonthConflict(exception)) {
                    throw exception;
                }
                skippedCount++;
                issues.add(syncIssue(assignmentId, "SKIPPED", "并发请求已生成该账期有效账单"));
                continue;
            }
            if (inserted == 1) {
                successCount++;
            } else {
                failureCount++;
                issues.add(syncIssue(assignmentId, "FAILURE", "停车账单生成失败"));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedCount", successCount);
        result.put("successCount", successCount);
        result.put("skippedCount", skippedCount);
        result.put("failureCount", failureCount);
        result.put("errors", issues);
        return ApiResponse.success(result);
    }

    private boolean hasActiveParkingBill(Long assignmentId, String billingMonth) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM parking_bill
                WHERE assignment_id = ?
                  AND billing_month = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                """,
                Long.class,
                assignmentId,
                billingMonth
        );
        return count != null && count > 0;
    }

    private boolean isActiveBillingMonthConflict(DuplicateKeyException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(ACTIVE_BILLING_MONTH_UNIQUE_KEY)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasUnresolvedActiveParkingBill(Long assignmentId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM parking_bill
                WHERE assignment_id = ?
                  AND billing_month IS NULL
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                """,
                Long.class,
                assignmentId
        );
        return count != null && count > 0;
    }

    @PostMapping("/parking/bills/{id}/pay")
    public ApiResponse<Void> payBill(@PathVariable Long id) {
        validateBillCanBePaid(requireParkingBillState(id));
        int updated = jdbcTemplate.update(
                """
                UPDATE parking_bill
                SET pay_status = 'PAID', updated_by = ?
                WHERE id = ?
                  AND deleted = 0
                  AND pay_status = 'UNPAID'
                  AND status IN ('ACTIVE', 'ENABLED')
                  AND amount > 0
                """,
                0L,
                id
        );
        if (updated == 0) {
            validateBillCanBePaid(requireParkingBillState(id));
            throw new BusinessException(409, "Parking bill state changed, please refresh and retry");
        }
        return ApiResponse.success();
    }

    @PostMapping("/parking/bills/{id}/void")
    public ApiResponse<Void> voidBill(@PathVariable Long id) {
        validateBillCanBeVoided(requireParkingBillState(id));
        int updated = jdbcTemplate.update(
                """
                UPDATE parking_bill
                SET status = 'VOID', updated_by = ?
                WHERE id = ?
                  AND deleted = 0
                  AND pay_status = 'UNPAID'
                  AND status IN ('ACTIVE', 'ENABLED')
                """,
                0L,
                id
        );
        if (updated == 0) {
            validateBillCanBeVoided(requireParkingBillState(id));
            throw new BusinessException(409, "Parking bill state changed, please refresh and retry");
        }
        return ApiResponse.success();
    }

    private Map<String, Object> requireParkingBillState(Long id) {
        List<Map<String, Object>> bills = jdbcTemplate.queryForList("""
                SELECT amount, pay_status AS payStatus, status, deleted
                FROM parking_bill
                WHERE id = ?
                LIMIT 1
                """, id);
        if (bills.isEmpty() || isDeleted(bills.getFirst().get("deleted"))) {
            throw new BusinessException(404, "Parking bill not found");
        }
        return bills.getFirst();
    }

    private void validateBillCanBePaid(Map<String, Object> bill) {
        String status = normalizedState(bill.get("status"));
        String payStatus = normalizedState(bill.get("payStatus"));
        if ("VOID".equals(status)) {
            throw new BusinessException(409, "Voided parking bill cannot be paid");
        }
        if ("PAID".equals(payStatus)) {
            throw new BusinessException(409, "Parking bill has already been paid");
        }
        if (!"UNPAID".equals(payStatus) || !isActiveState(status)) {
            throw new BusinessException(409, "Parking bill state does not allow payment");
        }
        BigDecimal amount = decimalValue(bill.get("amount"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(409, "Parking bill amount is invalid");
        }
    }

    private void validateBillCanBeVoided(Map<String, Object> bill) {
        String status = normalizedState(bill.get("status"));
        String payStatus = normalizedState(bill.get("payStatus"));
        if ("VOID".equals(status)) {
            throw new BusinessException(409, "Parking bill is already voided");
        }
        if ("PAID".equals(payStatus)) {
            throw new BusinessException(409, "Paid parking bill cannot be voided");
        }
        if (!"UNPAID".equals(payStatus) || !isActiveState(status)) {
            throw new BusinessException(409, "Parking bill state does not allow voiding");
        }
    }

    private String requiredBillingMonth(Map<String, Object> body, String dueDate) {
        String billingMonth = JdbcMaps.str(body, "billingMonth", "billing_month");
        if (billingMonth == null || billingMonth.isBlank()) {
            // Legacy compatibility only. New clients must submit billingMonth explicitly.
            billingMonth = dueDate.substring(0, 7);
        }
        return parseBillingMonth(billingMonth, "Parking billing month must use YYYY-MM").toString();
    }

    private YearMonth requiredSyncMonth(Map<String, Object> body) {
        String month = JdbcMaps.requiredStr(body, "Parking billing month is required", "month");
        return parseBillingMonth(month, "Parking billing month must use YYYY-MM");
    }

    private YearMonth parseBillingMonth(String value, String message) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, message);
        }
    }

    private BigDecimal requiredParkingMonthlyFee(Map<String, Object> body) {
        BigDecimal monthlyFee;
        try {
            monthlyFee = JdbcMaps.decimal(body, null, "monthlyFee", "monthly_fee");
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "Parking monthly fee is invalid");
        }
        return validateParkingMonthlyFee(monthlyFee);
    }

    private BigDecimal validateParkingMonthlyFee(BigDecimal monthlyFee) {
        if (monthlyFee == null || monthlyFee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "Parking monthly fee must be greater than zero");
        }
        if (monthlyFee.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(400, "Parking monthly fee supports at most 2 decimal places");
        }
        if (monthlyFee.compareTo(MAX_PARKING_MONTHLY_FEE) > 0) {
            throw new BusinessException(400, "Parking monthly fee exceeds database limit");
        }
        return monthlyFee;
    }

    private void requireActiveReference(String sql, String message, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        if (count == null || count == 0) {
            throw new BusinessException(400, message);
        }
    }

    private Map<String, Object> syncIssue(Long assignmentId, String result, String reason) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("assignmentId", assignmentId);
        issue.put("result", result);
        issue.put("reason", reason);
        return issue;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate localDateValue(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal requiredParkingBillAmount(
            Map<String, Object> body,
            String missingMessage,
            String... keys) {
        BigDecimal amount;
        try {
            amount = JdbcMaps.decimal(body, null, keys);
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "Parking bill amount is invalid");
        }
        if (amount == null) {
            throw new BusinessException(400, missingMessage);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "Parking bill amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(400, "Parking bill amount supports at most 2 decimal places");
        }
        if (amount.compareTo(MAX_PARKING_BILL_AMOUNT) > 0) {
            throw new BusinessException(400, "Parking bill amount exceeds database limit");
        }
        return amount;
    }

    private Long requiredParkingAssignmentId(Map<String, Object> body) {
        try {
            return JdbcMaps.requiredLong(body, "Parking assignment is required", "assignmentId");
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "Parking assignment is invalid");
        }
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isDeleted(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean isActiveState(String status) {
        return "ACTIVE".equals(status) || "ENABLED".equals(status);
    }

    private String normalizedState(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    }

    private void appendParkingSpaceFilters(
            StringBuilder where,
            List<Object> args,
            String keyword,
            String status,
            String area,
            String spaceNo,
            String floor,
            String type,
            String partyName,
            String plateNo,
            BigDecimal monthlyFee,
            BigDecimal minMonthlyFee,
            BigDecimal maxMonthlyFee
    ) {
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.space_no LIKE ? OR s.area LIKE ? OR s.location LIKE ? OR t.name LIKE ? OR a.car_plate_no LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            for (int index = 0; index < 5; index++) {
                args.add(likeKeyword);
            }
        }
        appendLikeFilter(where, args, "s.space_no", spaceNo);
        appendEqualsFilter(where, args, "s.rent_status", status);
        appendEqualsFilter(where, args, "s.area", area);
        appendEqualsFilter(where, args, "s.location", floor);
        appendEqualsFilter(where, args, "s.space_type", type);
        appendLikeFilter(where, args, "t.name", partyName);
        appendLikeFilter(where, args, "a.car_plate_no", plateNo);
        if (monthlyFee != null) {
            where.append(" AND a.monthly_fee = ?");
            args.add(monthlyFee);
        }
        if (minMonthlyFee != null) {
            where.append(" AND a.monthly_fee >= ?");
            args.add(minMonthlyFee);
        }
        if (maxMonthlyFee != null) {
            where.append(" AND a.monthly_fee <= ?");
            args.add(maxMonthlyFee);
        }
    }

    private void appendLikeFilter(StringBuilder where, List<Object> args, String expression, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" AND ").append(expression).append(" LIKE ?");
        args.add("%" + value.trim() + "%");
    }

    private void appendEqualsFilter(StringBuilder where, List<Object> args, String expression, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" AND ").append(expression).append(" = ?");
        args.add(value.trim());
    }

    private void saveSpace(Long id, Map<String, Object> body) {
        String spaceNo = JdbcMaps.requiredStr(body, "Space number is required", "spaceNo", "spaceCode");
        String area = JdbcMaps.requiredStr(body, "Parking area is required", "area");
        String location = requiredParkingLocation(body);
        String spaceType = JdbcMaps.strOr(body, "NORMAL", "type", "spaceType");
        BigDecimal monthlyRate = JdbcMaps.decimal(body, null, "monthlyRate");
        if (monthlyRate != null) {
            monthlyRate = validateParkingMonthlyFee(monthlyRate);
        }
        if (id == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO parking_space (
                        code, space_no, area, location, space_type, monthly_rate,
                        rent_status, status, created_by, updated_by, remark
                    )
                    VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE', 'ACTIVE', ?, ?, ?)
                    """,
                    JdbcMaps.code("PS"),
                    spaceNo,
                    area,
                    location,
                    spaceType,
                    monthlyRate,
                    0L,
                    0L,
                    JdbcMaps.str(body, "remark")
            );
            return;
        }
        ensureUpdated(jdbcTemplate.update(
                """
                UPDATE parking_space
                SET space_no = ?,
                    area = ?,
                    location = ?,
                    space_type = ?,
                    monthly_rate = ?,
                    updated_by = ?,
                    remark = ?
                WHERE id = ? AND deleted = 0
                """,
                spaceNo,
                area,
                location,
                spaceType,
                monthlyRate,
                0L,
                JdbcMaps.str(body, "remark"),
                id
        ), "Parking space not found");
    }

    private String requiredParkingLocation(Map<String, Object> body) {
        String location = JdbcMaps.requiredStr(body, "Parking location is required", "floor", "location");
        if (!List.of("B1", "B2", "地面").contains(location)) {
            throw new BusinessException(400, "Parking location must be B1, B2 or 地面");
        }
        return location;
    }

    private String normalizeSpaceTypeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return switch (text) {
            case "普通车位" -> "NORMAL";
            case "货车车位" -> "TRUCK";
            case "专用车位" -> "DEDICATED";
            case "月租车位" -> "MONTHLY";
            case "临停车位" -> "TEMPORARY";
            case "机械车位" -> "MECHANICAL";
            default -> text.toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
        };
    }

    private String requiredIsoDate(Map<String, Object> body, String key) {
        String value = JdbcMaps.requiredStr(body, "Parking bill due date is required", key);
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, "Parking bill due date must use YYYY-MM-DD");
        }
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }
}

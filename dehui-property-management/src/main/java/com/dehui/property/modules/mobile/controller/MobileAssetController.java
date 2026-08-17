package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MobileAssetController {

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldProjectionService customFieldProjectionService;

    public MobileAssetController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    @Autowired
    public MobileAssetController(
            JdbcTemplate jdbcTemplate,
            CustomFieldProjectionService customFieldProjectionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldProjectionService = customFieldProjectionService;
    }

    @GetMapping("/mobile/energy/summary")
    public ApiResponse<Map<String, Object>> energySummary(
            @RequestParam(required = false) String periodMonth,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        String period = normalizePeriod(periodMonth);
        Scope scope = energyScope(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT meter_record.meter_type AS meterType,
                       COALESCE(SUM(reading_record.usage_amount), 0) AS `usage`,
                       COALESCE(SUM(reading_record.settlement_amount), 0) AS amount,
                       COUNT(*) AS readingCount
                FROM energy_reading reading_record
                JOIN energy_meter meter_record
                  ON meter_record.id = reading_record.meter_id
                 AND meter_record.deleted = 0
                JOIN energy_meter_binding binding_record
                  ON binding_record.meter_id = reading_record.meter_id
                 AND binding_record.deleted = 0
                 AND binding_record.status IN ('ACTIVE', 'ENDED')
                 AND binding_record.effective_from <= reading_record.reading_date
                 AND (binding_record.effective_to IS NULL OR binding_record.effective_to >= reading_record.reading_date)
                WHERE reading_record.deleted = 0
                  AND reading_record.period_month = ?
                  AND (? = 1 OR binding_record.tenant_id = ?)
                GROUP BY meter_record.meter_type
                ORDER BY meter_record.meter_type
                """,
                period,
                scope.allData() ? 1 : 0,
                scope.tenantId()
        );
        return ApiResponse.success(Map.of(
                "periodMonth", period,
                "dataScope", scope.allData() ? "STAFF_RBAC" : "TENANT_COMPANY",
                "types", rows
        ));
    }

    @GetMapping("/mobile/energy/history")
    public ApiResponse<Map<String, Object>> energyHistory(
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) String periodFrom,
            @RequestParam(required = false) String periodTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Scope scope = energyScope(principal);
        List<Object> args = new ArrayList<>();
        args.add(scope.allData() ? 1 : 0);
        args.add(scope.tenantId());
        StringBuilder filters = new StringBuilder();
        if (meterType != null && !meterType.isBlank()) {
            filters.append(" AND meter_record.meter_type = ?");
            args.add(meterType.trim().toUpperCase());
        }
        if (periodFrom != null && !periodFrom.isBlank()) {
            filters.append(" AND reading_record.period_month >= ?");
            args.add(normalizePeriod(periodFrom));
        }
        if (periodTo != null && !periodTo.isBlank()) {
            filters.append(" AND reading_record.period_month <= ?");
            args.add(normalizePeriod(periodTo));
        }
        String groupedFrom = """
                FROM energy_reading reading_record
                JOIN energy_meter meter_record
                  ON meter_record.id = reading_record.meter_id
                 AND meter_record.deleted = 0
                JOIN energy_meter_binding binding_record
                  ON binding_record.meter_id = reading_record.meter_id
                 AND binding_record.deleted = 0
                 AND binding_record.status IN ('ACTIVE', 'ENDED')
                 AND binding_record.effective_from <= reading_record.reading_date
                 AND (binding_record.effective_to IS NULL OR binding_record.effective_to >= reading_record.reading_date)
                WHERE reading_record.deleted = 0
                  AND (? = 1 OR binding_record.tenant_id = ?)
                """ + filters;
        PageResponse<Map<String, Object>> history = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT reading_record.period_month AS periodMonth,
                       meter_record.meter_type AS meterType,
                       COALESCE(SUM(reading_record.usage_amount), 0) AS `usage`,
                       COALESCE(SUM(reading_record.settlement_amount), 0) AS amount,
                       COUNT(*) AS readingCount
                """ + groupedFrom + """
                GROUP BY reading_record.period_month, meter_record.meter_type
                ORDER BY reading_record.period_month DESC, meter_record.meter_type
                """,
                "SELECT COUNT(*) FROM (SELECT reading_record.period_month, meter_record.meter_type "
                        + groupedFrom
                        + " GROUP BY reading_record.period_month, meter_record.meter_type) grouped_history",
                args,
                page,
                pageSize
        );
        return ApiResponse.success(pageEnvelope(history, "history"));
    }

    @GetMapping("/mobile/energy/readings")
    public ApiResponse<PageResponse<Map<String, Object>>> energyReadings(
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) String periodMonth,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Scope scope = energyScope(principal);
        List<Object> args = new ArrayList<>();
        args.add(scope.allData() ? 1 : 0);
        args.add(scope.tenantId());
        StringBuilder filters = new StringBuilder();
        if (meterType != null && !meterType.isBlank()) {
            filters.append(" AND meter_record.meter_type = ?");
            args.add(meterType.trim().toUpperCase());
        }
        if (periodMonth != null && !periodMonth.isBlank()) {
            filters.append(" AND reading_record.period_month = ?");
            args.add(normalizePeriod(periodMonth));
        }
        String from = """
                FROM energy_reading reading_record
                INNER JOIN energy_meter meter_record
                  ON meter_record.id = reading_record.meter_id
                 AND meter_record.deleted = 0
                INNER JOIN energy_meter_binding binding_record
                  ON binding_record.meter_id = reading_record.meter_id
                 AND binding_record.deleted = 0
                 AND binding_record.effective_from <= reading_record.reading_date
                 AND (binding_record.effective_to IS NULL OR binding_record.effective_to >= reading_record.reading_date)
                WHERE reading_record.deleted = 0
                  AND (? = 1 OR binding_record.tenant_id = ?)
                """ + filters;
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT reading_record.id,
                       reading_record.period_month AS periodMonth,
                       reading_record.reading_date AS readingDate,
                       reading_record.previous_reading AS previousReading,
                       reading_record.current_reading AS currentReading,
                       reading_record.usage_amount AS `usage`,
                       reading_record.unit_price AS unitPriceSnapshot,
                       reading_record.settlement_amount AS amount,
                       meter_record.meter_no AS meterNo,
                       meter_record.meter_name AS meterName,
                       meter_record.meter_type AS meterType,
                       meter_record.unit,
                       meter_record.location_description AS location
                """ + from + """
                ORDER BY reading_record.reading_date DESC, reading_record.id DESC
                """,
                "SELECT COUNT(*) " + from,
                args,
                page,
                pageSize
        );
        return ApiResponse.success(customFieldProjectionService == null
                ? result
                : customFieldProjectionService.attach("energy-records", result));
    }

    @GetMapping("/mobile/energy/details/{id}")
    public ApiResponse<Map<String, Object>> energyDetail(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Scope scope = energyScope(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT reading_record.id,
                       reading_record.period_month AS periodMonth,
                       reading_record.reading_date AS readingDate,
                       reading_record.previous_reading AS previousReading,
                       reading_record.current_reading AS currentReading,
                       reading_record.usage_amount AS `usage`,
                       reading_record.unit_price AS unitPriceSnapshot,
                       reading_record.settlement_amount AS amount,
                       reading_record.bill_status AS basisStatus,
                       meter_record.meter_no AS meterNo,
                       meter_record.meter_type AS meterType,
                       meter_record.unit
                FROM energy_reading reading_record
                JOIN energy_meter meter_record ON meter_record.id = reading_record.meter_id
                JOIN energy_meter_binding binding_record
                  ON binding_record.meter_id = reading_record.meter_id
                 AND binding_record.deleted = 0
                 AND binding_record.status IN ('ACTIVE', 'ENDED')
                 AND binding_record.effective_from <= reading_record.reading_date
                 AND (binding_record.effective_to IS NULL OR binding_record.effective_to >= reading_record.reading_date)
                WHERE reading_record.id = ?
                  AND reading_record.deleted = 0
                  AND (? = 1 OR binding_record.tenant_id = ?)
                LIMIT 1
                """,
                id,
                scope.allData() ? 1 : 0,
                scope.tenantId()
        );
        if (rows.isEmpty()) {
            throw BusinessException.forbidden("Energy reading is outside the current identity data scope");
        }
        Map<String, Object> detail = new LinkedHashMap<>(rows.getFirst());
        if (customFieldProjectionService != null) {
            detail = customFieldProjectionService.attach("energy-records", List.of(detail)).getFirst();
        } else {
            detail.put("customFields", Map.of());
        }
        return ApiResponse.success(detail);
    }

    @GetMapping("/mobile/parking/company")
    public ApiResponse<Map<String, Object>> companyParking(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        if (principal != null && "STAFF".equals(principal.userType())) {
            MobileApiSupport.requireCapability(principal, "parking.staff.view");
            MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
            return ApiResponse.success(companyParkingEnvelope("1 = 1", List.of(), page, pageSize));
        }
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        MobileApiSupport.requireCapability(principal, "parking.company.view");
        return ApiResponse.success(companyParkingEnvelope(
                "assignment_record.tenant_id = ?",
                List.of(tenantId),
                page,
                pageSize
        ));
    }

    @GetMapping("/mobile/parking/mine")
    public ApiResponse<Map<String, Object>> myParking(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        MobileApiSupport.requireCapability(principal, "parking.mine.view");
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        Long contactId = MobileApiSupport.tenantContactId(jdbcTemplate, principal);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(principal.userId());
        args.add(contactId);
        args.add(contactId);
        return ApiResponse.success(pageEnvelope(parkingRows(
                """
                assignment_record.tenant_id = ?
                AND (
                    assignment_record.mobile_user_id = ?
                    OR (? IS NOT NULL AND assignment_record.tenant_contact_id = ?)
                )
                """,
                args,
                page,
                pageSize
        ), "parking"));
    }

    @GetMapping("/mobile/parking/{id}")
    public ApiResponse<Map<String, Object>> parkingDetail(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        String scopeSql;
        List<Object> args = new ArrayList<>();
        args.add(id);
        if (principal != null && "STAFF".equals(principal.userType())) {
            MobileApiSupport.requireCapability(principal, "parking.staff.view");
            MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
            scopeSql = "1 = 1";
        } else {
            Long tenantId = MobileApiSupport.requireTenantId(principal);
            boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal)
                    && principal.permissions().contains("parking.company.view");
            if (companyScope) {
                scopeSql = "assignment_record.tenant_id = ?";
                args.add(tenantId);
            } else {
                MobileApiSupport.requireCapability(principal, "parking.mine.view");
                Long contactId = MobileApiSupport.tenantContactId(jdbcTemplate, principal);
                scopeSql = """
                        assignment_record.tenant_id = ?
                        AND (assignment_record.mobile_user_id = ?
                             OR (? IS NOT NULL AND assignment_record.tenant_contact_id = ?))
                        """;
                args.add(tenantId);
                args.add(principal.userId());
                args.add(contactId);
                args.add(contactId);
            }
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                parkingSelect() + """
                WHERE assignment_record.id = ?
                  AND assignment_record.deleted = 0
                  AND
                """ + scopeSql + "\nLIMIT 1",
                args.toArray()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "车位不存在或不在当前身份数据范围");
        }
        Map<String, Object> detail = new LinkedHashMap<>(rows.getFirst());
        if (customFieldProjectionService != null) {
            detail = customFieldProjectionService.attach("parking-assignments", List.of(detail)).getFirst();
        } else {
            detail.put("customFields", Map.of());
        }
        return ApiResponse.success(detail);
    }

    private PageResponse<Map<String, Object>> parkingRows(
            String scopeSql,
            List<Object> args,
            Integer page,
            Integer pageSize) {
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                parkingSelect() + """
                WHERE assignment_record.deleted = 0
                  AND assignment_record.status IN ('ACTIVE', 'ENABLED')
                  AND assignment_record.start_date <= CURRENT_DATE
                  AND (assignment_record.end_date IS NULL OR assignment_record.end_date >= CURRENT_DATE)
                  AND
                """ + scopeSql + "\nORDER BY space_record.space_no, assignment_record.id",
                """
                SELECT COUNT(*)
                FROM parking_assignment assignment_record
                JOIN parking_space space_record
                  ON space_record.id = assignment_record.parking_space_id
                 AND space_record.deleted = 0
                WHERE assignment_record.deleted = 0
                  AND assignment_record.status IN ('ACTIVE', 'ENABLED')
                  AND assignment_record.start_date <= CURRENT_DATE
                  AND (assignment_record.end_date IS NULL OR assignment_record.end_date >= CURRENT_DATE)
                  AND
                """ + scopeSql,
                args,
                page,
                pageSize
        );
        return customFieldProjectionService == null
                ? result
                : customFieldProjectionService.attach("parking-assignments", result);
    }

    private Map<String, Object> companyParkingEnvelope(
            String scopeSql,
            List<Object> args,
            Integer page,
            Integer pageSize) {
        Map<String, Object> result = pageEnvelope(parkingRows(scopeSql, args, page, pageSize), "parking");
        result.put("summary", parkingSummary(scopeSql, args));
        return result;
    }

    private Map<String, Object> parkingSummary(String scopeSql, List<Object> args) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS totalCount,
                       COALESCE(SUM(CASE
                           WHEN assignment_record.tenant_contact_id IS NOT NULL
                             OR assignment_record.mobile_user_id IS NOT NULL
                           THEN 1 ELSE 0
                       END), 0) AS fixedCount,
                       COALESCE(SUM(CASE
                           WHEN assignment_record.tenant_contact_id IS NULL
                            AND assignment_record.mobile_user_id IS NULL
                           THEN 1 ELSE 0
                       END), 0) AS unboundCount
                FROM parking_assignment assignment_record
                JOIN parking_space space_record
                  ON space_record.id = assignment_record.parking_space_id
                 AND space_record.deleted = 0
                WHERE assignment_record.deleted = 0
                  AND assignment_record.status IN ('ACTIVE', 'ENABLED')
                  AND assignment_record.start_date <= CURRENT_DATE
                  AND (assignment_record.end_date IS NULL OR assignment_record.end_date >= CURRENT_DATE)
                  AND
                """ + scopeSql,
                args.toArray()
        );
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", countValue(row.get("totalCount")));
        summary.put("fixedCount", countValue(row.get("fixedCount")));
        summary.put("unboundCount", countValue(row.get("unboundCount")));
        return summary;
    }

    private long countValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String parkingSelect() {
        return
                """
                SELECT assignment_record.id AS assignmentId,
                       assignment_record.id,
                       space_record.space_no AS spaceNo,
                       space_record.area,
                       space_record.location AS floor,
                       space_record.space_type AS spaceType,
                       assignment_record.car_plate_no AS plateNumber,
                       assignment_record.vehicle_type AS vehicleType,
                       assignment_record.start_date AS effectiveFrom,
                       assignment_record.end_date AS effectiveTo,
                       assignment_record.unit_price_snapshot AS unitPriceSnapshot,
                       contact_record.name AS contactName
                FROM parking_assignment assignment_record
                JOIN parking_space space_record
                  ON space_record.id = assignment_record.parking_space_id
                 AND space_record.deleted = 0
                LEFT JOIN tenant_contact contact_record
                  ON contact_record.id = assignment_record.tenant_contact_id
                 AND contact_record.deleted = 0
                """;
    }

    private Map<String, Object> pageEnvelope(PageResponse<Map<String, Object>> page, String legacyKey) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.records());
        result.put(legacyKey, page.records());
        result.put("page", page.page());
        result.put("pageSize", page.pageSize());
        result.put("total", page.total());
        return result;
    }

    private Scope energyScope(AuthPrincipal principal) {
        if (principal != null && "TENANT".equals(principal.userType())) {
            MobileApiSupport.requireCapability(principal, "energy.company.view");
            return new Scope(false, MobileApiSupport.requireTenantId(principal));
        }
        if (principal != null && List.of("STAFF", "INTERNAL").contains(principal.userType())) {
            MobileApiSupport.requireCapability(principal, "energy.staff.view");
            MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
            return new Scope(true, null);
        }
        throw BusinessException.forbidden("Energy data is not available to the current mobile identity");
    }

    private String normalizePeriod(String periodMonth) {
        if (periodMonth == null || periodMonth.isBlank()) {
            return YearMonth.now().toString();
        }
        try {
            return YearMonth.parse(periodMonth.trim()).toString();
        } catch (RuntimeException exception) {
            throw new BusinessException(400, "periodMonth must use YYYY-MM");
        }
    }

    private record Scope(boolean allData, Long tenantId) {
    }
}

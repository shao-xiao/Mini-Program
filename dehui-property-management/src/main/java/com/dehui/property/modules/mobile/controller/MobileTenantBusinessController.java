package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MobileTenantBusinessController {

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldProjectionService customFieldProjectionService;

    @GetMapping("/mobile/my-business/summary")
    public ApiResponse<Map<String, Object>> summary(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("dataScope", companyScope ? "TENANT_COMPANY" : "SELF");
        result.put("companyName", companyScope ? tenantName(tenantId) : null);

        if (companyScope) {
            result.put("effectiveRoomCount", count("""
                    SELECT COUNT(DISTINCT room_record.id)
                    FROM building_room room_record
                    INNER JOIN contract contract_record
                      ON contract_record.tenant_id = ?
                     AND contract_record.deleted = 0
                     AND contract_record.status IN ('ACTIVE', 'APPROVED')
                     AND (
                        contract_record.room_id = room_record.id
                        OR EXISTS (
                            SELECT 1 FROM contract_room link_record
                            WHERE link_record.contract_id = contract_record.id
                              AND link_record.room_id = room_record.id
                              AND link_record.deleted = 0
                        )
                     )
                    WHERE room_record.deleted = 0
                    """, tenantId));
            result.put("effectiveContractCount", count(
                    "SELECT COUNT(*) FROM contract WHERE tenant_id = ? AND deleted = 0 AND status IN ('ACTIVE', 'APPROVED')",
                    tenantId
            ));
            if (has(principal, "bill.view")) {
                Map<String, Object> pending = jdbcTemplate.queryForMap("""
                        SELECT COUNT(*) AS count,
                               COALESCE(SUM(GREATEST(amount - paid_amount, 0)), 0) AS amount
                        FROM bill
                        WHERE tenant_id = ?
                          AND deleted = 0
                          AND pay_status NOT IN ('PAID', 'CANCELLED')
                        """, tenantId);
                result.put("pendingBillCount", pending.get("count"));
                result.put("pendingBillAmount", pending.get("amount"));
            }
            if (has(principal, "energy.company.view")) {
                result.put("currentMonthEnergy", jdbcTemplate.queryForList("""
                        SELECT meter_record.meter_type AS meterType,
                               COALESCE(SUM(reading_record.usage_amount), 0) AS `usage`,
                               COALESCE(SUM(reading_record.settlement_amount), 0) AS amount
                        FROM energy_reading reading_record
                        INNER JOIN energy_meter meter_record
                          ON meter_record.id = reading_record.meter_id
                         AND meter_record.deleted = 0
                        INNER JOIN energy_meter_binding binding_record
                          ON binding_record.meter_id = reading_record.meter_id
                         AND binding_record.tenant_id = ?
                         AND binding_record.deleted = 0
                         AND binding_record.effective_from <= reading_record.reading_date
                         AND (binding_record.effective_to IS NULL OR binding_record.effective_to >= reading_record.reading_date)
                        WHERE reading_record.deleted = 0
                          AND reading_record.period_month = ?
                        GROUP BY meter_record.meter_type
                        ORDER BY meter_record.meter_type
                        """, tenantId, YearMonth.now().toString()));
            }
        }

        result.put("parkingCount", has(principal, companyScope ? "parking.company.view" : "parking.mine.view")
                ? parkingCount(principal, tenantId, companyScope)
                : null);
        result.put("upcomingMeetingCount", count("""
                SELECT COUNT(*)
                FROM meeting_booking
                WHERE tenant_id = ?
                  AND (? = 1 OR applicant_mobile_user_id = ?)
                  AND deleted = 0
                  AND booking_status NOT IN ('CANCELLED', 'REJECTED', 'COMPLETED')
                  AND end_time >= NOW()
                """, tenantId, companyScope ? 1 : 0, principal.userId()));
        result.put("upcomingVisitorCount", count("""
                SELECT COUNT(*)
                FROM visitor_appointment
                WHERE tenant_id = ?
                  AND (? = 1 OR applicant_mobile_user_id = ?)
                  AND deleted = 0
                  AND appointment_status NOT IN ('LEFT', 'CANCELLED')
                  AND visit_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)
                """, tenantId, companyScope ? 1 : 0, principal.userId()));
        result.put("openWorkOrderCount", count("""
                SELECT COUNT(*)
                FROM work_order
                WHERE tenant_id = ?
                  AND (? = 1 OR submitter_mobile_user_id = ?)
                  AND deleted = 0
                  AND work_status NOT IN ('COMPLETED', 'CLOSED', 'CANCELLED')
                """, tenantId, companyScope ? 1 : 0, principal.userId()));
        return ApiResponse.success(result);
    }

    @GetMapping("/mobile/my-business/rooms")
    public ApiResponse<PageResponse<Map<String, Object>>> rooms(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        Long tenantId = requireCompanyScope(principal);
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                roomSelect() + """
                        WHERE room_record.deleted = 0
                          AND contract_record.tenant_id = ?
                          AND contract_record.deleted = 0
                          AND contract_record.status NOT IN ('CANCELLED', 'TERMINATED')
                        GROUP BY room_record.id, building_record.name, floor_record.name
                        ORDER BY MAX(contract_record.updated_at) DESC, room_record.id DESC
                        """,
                """
                        SELECT COUNT(DISTINCT room_record.id)
                        FROM building_room room_record
                        INNER JOIN contract contract_record
                          ON (
                            contract_record.room_id = room_record.id
                            OR EXISTS (
                                SELECT 1 FROM contract_room link_record
                                WHERE link_record.contract_id = contract_record.id
                                  AND link_record.room_id = room_record.id
                                  AND link_record.deleted = 0
                            )
                          )
                        WHERE room_record.deleted = 0
                          AND contract_record.tenant_id = ?
                          AND contract_record.deleted = 0
                          AND contract_record.status NOT IN ('CANCELLED', 'TERMINATED')
                        """,
                List.of(tenantId),
                page,
                pageSize
        );
        return ApiResponse.success(customFieldProjectionService.attach("rooms", result));
    }

    @GetMapping("/mobile/my-business/rooms/{id}")
    public ApiResponse<Map<String, Object>> room(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        Long tenantId = requireCompanyScope(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                roomSelect() + """
                        WHERE room_record.id = ?
                          AND room_record.deleted = 0
                          AND contract_record.tenant_id = ?
                          AND contract_record.deleted = 0
                          AND contract_record.status NOT IN ('CANCELLED', 'TERMINATED')
                        GROUP BY room_record.id, building_record.name, floor_record.name
                        LIMIT 1
                        """,
                id,
                tenantId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "房间不存在或不在当前租户范围");
        }
        return ApiResponse.success(customFieldProjectionService.attach("rooms", rows).getFirst());
    }

    private String roomSelect() {
        return """
                SELECT room_record.id,
                       room_record.code AS roomCode,
                       building_record.name AS buildingName,
                       floor_record.name AS floorName,
                       room_record.room_no AS roomNumber,
                       room_record.area,
                       room_record.property_fee_area AS propertyFeeArea,
                       room_record.rent_status AS leaseStatus,
                       MIN(contract_record.start_date) AS contractStartDate,
                       MAX(contract_record.end_date) AS contractEndDate,
                       GROUP_CONCAT(DISTINCT contract_record.code ORDER BY contract_record.code SEPARATOR '、') AS contractNumbers
                FROM building_room room_record
                LEFT JOIN building building_record
                  ON building_record.id = room_record.building_id
                 AND building_record.deleted = 0
                LEFT JOIN building_floor floor_record
                  ON floor_record.id = room_record.floor_id
                 AND floor_record.deleted = 0
                INNER JOIN contract contract_record
                  ON (
                    contract_record.room_id = room_record.id
                    OR EXISTS (
                        SELECT 1 FROM contract_room link_record
                        WHERE link_record.contract_id = contract_record.id
                          AND link_record.room_id = room_record.id
                          AND link_record.deleted = 0
                    )
                  )
                """;
    }

    private Long requireCompanyScope(AuthPrincipal principal) {
        MobileApiSupport.requireCapability(principal, "contract.view");
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        if (!MobileApiSupport.isTenantManager(jdbcTemplate, principal)) {
            throw BusinessException.forbidden("普通租户员工无权查看企业房间");
        }
        return tenantId;
    }

    private long parkingCount(AuthPrincipal principal, Long tenantId, boolean companyScope) {
        if (companyScope) {
            return count("""
                    SELECT COUNT(*) FROM parking_assignment
                    WHERE tenant_id = ? AND deleted = 0
                      AND status IN ('ACTIVE', 'ENABLED')
                      AND start_date <= CURRENT_DATE
                      AND (end_date IS NULL OR end_date >= CURRENT_DATE)
                    """, tenantId);
        }
        Long contactId = MobileApiSupport.tenantContactId(jdbcTemplate, principal);
        return count("""
                SELECT COUNT(*) FROM parking_assignment
                WHERE tenant_id = ? AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                  AND start_date <= CURRENT_DATE
                  AND (end_date IS NULL OR end_date >= CURRENT_DATE)
                  AND (mobile_user_id = ? OR (? IS NOT NULL AND tenant_contact_id = ?))
                """, tenantId, principal.userId(), contactId, contactId);
    }

    private String tenantName(Long tenantId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT name FROM tenant WHERE id = ? AND deleted = 0 LIMIT 1",
                String.class,
                tenantId
        );
        return rows.isEmpty() ? "" : rows.getFirst();
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private boolean has(AuthPrincipal principal, String capability) {
        return principal != null
                && principal.permissions() != null
                && principal.permissions().contains(capability);
    }
}

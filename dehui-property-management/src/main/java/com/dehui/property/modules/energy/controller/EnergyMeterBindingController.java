package com.dehui.property.modules.energy.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/energy/meters/{meterId}/bindings")
@RequiredArgsConstructor
public class EnergyMeterBindingController {

    private static final Set<String> SUBJECT_TYPES = Set.of("PROPERTY", "TENANT", "ROOM", "PUBLIC_AREA");

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> bindings(@PathVariable Long meterId) {
        requireMeter(meterId, false);
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT binding_record.id,
                       binding_record.code,
                       binding_record.meter_id AS meterId,
                       binding_record.subject_type AS subjectType,
                       binding_record.tenant_id AS tenantId,
                       tenant_record.name AS tenantName,
                       binding_record.room_id AS roomId,
                       room_record.room_no AS roomCode,
                       binding_record.effective_from AS effectiveFrom,
                       binding_record.effective_to AS effectiveTo,
                       binding_record.status,
                       binding_record.remark
                FROM energy_meter_binding binding_record
                LEFT JOIN tenant tenant_record
                  ON tenant_record.id = binding_record.tenant_id
                 AND tenant_record.deleted = 0
                LEFT JOIN building_room room_record
                  ON room_record.id = binding_record.room_id
                 AND room_record.deleted = 0
                WHERE binding_record.meter_id = ?
                  AND binding_record.deleted = 0
                ORDER BY binding_record.effective_from DESC, binding_record.id DESC
                """,
                meterId
        ));
    }

    @PostMapping("")
    @Transactional
    public ApiResponse<Map<String, Object>> createBinding(
            @PathVariable Long meterId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Map<String, Object> meter = requireMeter(meterId, true);
        BindingInput input = bindingInput(body);
        ensureAttributionNotBeforeCommissioning(meter, input.effectiveFrom());
        ensureNoOverlap(meterId, input.effectiveFrom(), input.effectiveTo());
        Long id = insertBinding(meterId, input, principal.userId());
        syncLegacyCurrentTenant(meterId, principal.userId());
        return ApiResponse.success(Map.of("id", id));
    }

    @PostMapping("/replace")
    @Transactional
    public ApiResponse<Map<String, Object>> replaceBinding(
            @PathVariable Long meterId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Map<String, Object> meter = requireMeter(meterId, true);
        BindingInput input = bindingInput(body);
        ensureAttributionNotBeforeCommissioning(meter, input.effectiveFrom());
        Map<String, Object> current = currentOpenBinding(meterId);
        if (current != null) {
            LocalDate currentFrom = dateValue(current.get("effectiveFrom"));
            if (currentFrom != null && !input.effectiveFrom().isAfter(currentFrom)) {
                throw new BusinessException(
                        409,
                        "新的计量归属开始日期必须晚于当前归属开始日期 " + currentFrom
                );
            }
            if (sameAttributionTarget(current, input)) {
                throw new BusinessException(409, "新计量归属与当前归属相同，无需重复变更");
            }
        }
        LocalDate previousEnd = input.effectiveFrom().minusDays(1);
        if (current != null) {
            jdbcTemplate.update(
                    """
                    UPDATE energy_meter_binding
                    SET effective_to = ?,
                        status = 'ENDED',
                        updated_by = ?
                    WHERE id = ?
                      AND meter_id = ?
                      AND deleted = 0
                      AND status = 'ACTIVE'
                      AND effective_to IS NULL
                    """,
                    Date.valueOf(previousEnd),
                    principal.userId(),
                    longValue(current.get("id")),
                    meterId
            );
        }
        ensureNoOverlap(meterId, input.effectiveFrom(), input.effectiveTo());
        Long id = insertBinding(meterId, input, principal.userId());
        syncLegacyCurrentTenant(meterId, principal.userId());
        return ApiResponse.success(Map.of("id", id));
    }

    private Long insertBinding(Long meterId, BindingInput input, Long operatorId) {
        String code = JdbcMaps.code("EMB");
        jdbcTemplate.update(
                """
                INSERT INTO energy_meter_binding (
                    code, meter_id, subject_type, tenant_id, room_id,
                    effective_from, effective_to, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                code,
                meterId,
                input.subjectType(),
                input.tenantId(),
                input.roomId(),
                Date.valueOf(input.effectiveFrom()),
                input.effectiveTo() == null ? null : Date.valueOf(input.effectiveTo()),
                operatorId,
                operatorId,
                input.remark()
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM energy_meter_binding WHERE code = ? AND deleted = 0 LIMIT 1",
                Long.class,
                code
        );
    }

    private BindingInput bindingInput(Map<String, Object> body) {
        String subjectType = JdbcMaps.requiredStr(body, "计量主体类型不能为空", "subjectType")
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!SUBJECT_TYPES.contains(subjectType)) {
            throw new BusinessException(400, "计量主体类型不正确");
        }
        Long tenantId = JdbcMaps.longVal(body, "tenantId");
        Long roomId = JdbcMaps.longVal(body, "roomId");
        if ("TENANT".equals(subjectType) && tenantId == null) {
            throw new BusinessException(400, "租户计量主体必须选择租户");
        }
        if (!"TENANT".equals(subjectType) && tenantId != null) {
            throw new BusinessException(400, "非租户计量主体不能绑定租户");
        }
        if (tenantId != null) {
            requireReference("tenant", tenantId, "租户不存在或已停用");
        }
        if (roomId != null) {
            requireReference("building_room", roomId, "房间不存在或已停用");
        }
        LocalDate effectiveFrom = date(
                JdbcMaps.requiredStr(body, "计量归属开始日期不能为空", "effectiveFrom"),
                "计量归属开始日期格式必须为 YYYY-MM-DD"
        );
        String effectiveToValue = JdbcMaps.str(body, "effectiveTo");
        LocalDate effectiveTo = effectiveToValue == null
                ? null
                : date(effectiveToValue, "计量归属结束日期格式必须为 YYYY-MM-DD");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(400, "计量归属结束日期不能早于开始日期");
        }
        return new BindingInput(
                subjectType,
                tenantId,
                roomId,
                effectiveFrom,
                effectiveTo,
                JdbcMaps.str(body, "remark")
        );
    }

    private void ensureNoOverlap(Long meterId, LocalDate effectiveFrom, LocalDate effectiveTo) {
        Long overlaps = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM energy_meter_binding
                WHERE meter_id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENDED')
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                """,
                Long.class,
                meterId,
                Date.valueOf(effectiveTo == null ? LocalDate.of(9999, 12, 31) : effectiveTo),
                Date.valueOf(effectiveFrom)
        );
        if (overlaps != null && overlaps > 0) {
            throw new BusinessException(
                    409,
                    "所选计量归属开始日期与现有归属期间重叠，请查看计量归属历史后调整"
            );
        }
    }

    private Map<String, Object> requireMeter(Long meterId, boolean lock) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       commissioning_date AS commissioningDate
                FROM energy_meter
                WHERE id = ? AND deleted = 0
                """ + (lock ? " FOR UPDATE" : ""),
                meterId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "表具不存在");
        }
        return rows.getFirst();
    }

    private void ensureAttributionNotBeforeCommissioning(
            Map<String, Object> meter,
            LocalDate effectiveFrom) {
        LocalDate commissioningDate = dateValue(meter.get("commissioningDate"));
        if (commissioningDate != null && effectiveFrom.isBefore(commissioningDate)) {
            throw new BusinessException(
                    400,
                    "计量归属开始日期不能早于表具投运日期 " + commissioningDate
            );
        }
    }

    private Map<String, Object> currentOpenBinding(Long meterId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       subject_type AS subjectType,
                       tenant_id AS tenantId,
                       room_id AS roomId,
                       effective_from AS effectiveFrom
                FROM energy_meter_binding
                WHERE meter_id = ?
                  AND deleted = 0
                  AND status = 'ACTIVE'
                  AND effective_to IS NULL
                ORDER BY effective_from DESC, id DESC
                LIMIT 1
                """,
                meterId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean sameAttributionTarget(Map<String, Object> current, BindingInput input) {
        return Objects.equals(String.valueOf(current.get("subjectType")), input.subjectType())
                && Objects.equals(longValue(current.get("tenantId")), input.tenantId())
                && Objects.equals(longValue(current.get("roomId")), input.roomId());
    }

    private void requireReference(String table, Long id, String message) {
        String sql = switch (table) {
            case "tenant" -> "SELECT COUNT(*) FROM tenant WHERE id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')";
            case "building_room" -> "SELECT COUNT(*) FROM building_room WHERE id = ? AND deleted = 0";
            default -> throw new IllegalArgumentException("Unsupported reference table");
        };
        Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
        if (count == null || count == 0) {
            throw new BusinessException(400, message);
        }
    }

    private void syncLegacyCurrentTenant(Long meterId, Long operatorId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT tenant_id AS tenantId
                FROM energy_meter_binding
                WHERE meter_id = ?
                  AND deleted = 0
                  AND status = 'ACTIVE'
                  AND effective_from <= CURRENT_DATE
                  AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
                ORDER BY effective_from DESC, id DESC
                LIMIT 1
                """,
                meterId
        );
        Long tenantId = rows.isEmpty() ? null : longValue(rows.getFirst().get("tenantId"));
        jdbcTemplate.update(
                "UPDATE energy_meter SET tenant_id = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                tenantId,
                operatorId,
                meterId
        );
    }

    private LocalDate date(String value, String message) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, message);
        }
    }

    private LocalDate dateValue(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value == null) {
            return null;
        }
        return date(String.valueOf(value), "日期格式必须为 YYYY-MM-DD");
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private record BindingInput(
            String subjectType,
            Long tenantId,
            Long roomId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String remark
    ) {
    }
}

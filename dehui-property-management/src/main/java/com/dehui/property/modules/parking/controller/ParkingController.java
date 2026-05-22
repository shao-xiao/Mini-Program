package com.dehui.property.modules.parking.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ParkingController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/parking/spaces")
    public ApiResponse<List<Map<String, Object>>> spaces() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT s.id, s.code, s.space_no AS spaceNo, s.location AS floor, s.location,
                       s.space_type AS type, s.space_type AS spaceType, s.rent_status AS status, s.remark
                FROM parking_space s
                WHERE s.deleted = 0
                ORDER BY s.space_no ASC, s.id DESC
                """
        ));
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
        ensureUpdated(jdbcTemplate.update("UPDATE parking_space SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Parking space not found");
        return ApiResponse.success();
    }

    @PatchMapping("/parking/spaces/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        ensureUpdated(jdbcTemplate.update("UPDATE parking_space SET rent_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", status, 0L, id), "Parking space not found");
        return ApiResponse.success();
    }

    @PostMapping("/parking/spaces/{id}/bind")
    public ApiResponse<Void> bind(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tenantId = JdbcMaps.longVal(body, "partyId", "tenantId");
        if (tenantId == null) {
            List<Map<String, Object>> tenant = jdbcTemplate.queryForList("SELECT id FROM tenant WHERE deleted = 0 ORDER BY id ASC LIMIT 1");
            tenantId = tenant.isEmpty() ? null : ((Number) tenant.getFirst().get("id")).longValue();
        }
        if (tenantId == null) {
            throw new BusinessException(400, "Tenant is required before binding parking space");
        }
        jdbcTemplate.update(
                """
                INSERT INTO parking_assignment (code, parking_space_id, tenant_id, car_plate_no, start_date, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("PAS"),
                id,
                tenantId,
                JdbcMaps.requiredStr(body, "Car plate is required", "plateNo", "carPlateNo"),
                JdbcMaps.date(body, JdbcMaps.today(), "startDate"),
                0L,
                0L,
                JdbcMaps.str(body, "remark")
        );
        jdbcTemplate.update("UPDATE parking_space SET rent_status = 'OCCUPIED', updated_by = ? WHERE id = ?", 0L, id);
        return ApiResponse.success();
    }

    @PostMapping("/parking/spaces/{id}/release")
    public ApiResponse<Void> release(@PathVariable Long id) {
        jdbcTemplate.update("UPDATE parking_assignment SET status = 'RELEASED', end_date = ?, updated_by = ? WHERE parking_space_id = ? AND deleted = 0", JdbcMaps.today(), 0L, id);
        ensureUpdated(jdbcTemplate.update("UPDATE parking_space SET rent_status = 'AVAILABLE', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Parking space not found");
        return ApiResponse.success();
    }

    @GetMapping("/parking/assignments")
    public ApiResponse<List<Map<String, Object>>> assignments() {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT * FROM parking_assignment WHERE deleted = 0 ORDER BY id DESC"));
    }

    @GetMapping("/parking/bills")
    public ApiResponse<List<Map<String, Object>>> bills() {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT * FROM parking_bill WHERE deleted = 0 ORDER BY id DESC"));
    }

    @PostMapping("/parking/bills/sync")
    public ApiResponse<Map<String, Object>> syncBills(@RequestBody(required = false) Map<String, Object> body) {
        List<Map<String, Object>> assignments = jdbcTemplate.queryForList("SELECT id, tenant_id FROM parking_assignment WHERE deleted = 0 AND status = 'ACTIVE'");
        int count = 0;
        for (Map<String, Object> assignment : assignments) {
            jdbcTemplate.update(
                    """
                    INSERT INTO parking_bill (code, assignment_id, tenant_id, amount, due_date, pay_status, status, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, 'UNPAID', 'ACTIVE', ?, ?)
                    """,
                    JdbcMaps.code("PB"),
                    assignment.get("id"),
                    assignment.get("tenant_id"),
                    JdbcMaps.decimal(body, BigDecimal.ZERO, "amount", "monthlyFee"),
                    JdbcMaps.today(),
                    0L,
                    0L
            );
            count++;
        }
        return ApiResponse.success(Map.of("generatedCount", count));
    }

    @PostMapping("/parking/bills/{id}/pay")
    public ApiResponse<Void> payBill(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE parking_bill SET pay_status = 'PAID', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Parking bill not found");
        return ApiResponse.success();
    }

    @PostMapping("/parking/bills/{id}/void")
    public ApiResponse<Void> voidBill(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE parking_bill SET status = 'VOID', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Parking bill not found");
        return ApiResponse.success();
    }

    private void saveSpace(Long id, Map<String, Object> body) {
        String spaceNo = JdbcMaps.requiredStr(body, "Space number is required", "spaceNo", "spaceCode");
        if (id == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO parking_space (code, space_no, location, space_type, rent_status, status, created_by, updated_by, remark)
                    VALUES (?, ?, ?, ?, 'AVAILABLE', 'ACTIVE', ?, ?, ?)
                    """,
                    JdbcMaps.code("PS"),
                    spaceNo,
                    JdbcMaps.str(body, "floor", "location", "area"),
                    JdbcMaps.strOr(body, "NORMAL", "type", "spaceType"),
                    0L,
                    0L,
                    JdbcMaps.str(body, "remark")
            );
            return;
        }
        ensureUpdated(jdbcTemplate.update(
                """
                UPDATE parking_space SET space_no = ?, location = ?, space_type = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                spaceNo,
                JdbcMaps.str(body, "floor", "location", "area"),
                JdbcMaps.strOr(body, "NORMAL", "type", "spaceType"),
                0L,
                JdbcMaps.str(body, "remark"),
                id
        ), "Parking space not found");
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }
}

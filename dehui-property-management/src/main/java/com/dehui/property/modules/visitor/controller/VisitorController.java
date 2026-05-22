package com.dehui.property.modules.visitor.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VisitorController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping({"/visitors", "/mobile/visitors"})
    public ApiResponse<List<Map<String, Object>>> visitors() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, tenant_id AS tenantId, visitor_name AS visitorName, visitor_phone AS visitorPhone,
                       car_plate_no AS carPlateNo, visit_time AS visitTime, appointment_status AS status, remark
                FROM visitor_appointment
                WHERE deleted = 0
                ORDER BY visit_time DESC, id DESC
                """
        ));
    }

    @PostMapping("/visitors")
    public ApiResponse<Void> create(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO visitor_appointment (code, tenant_id, visitor_name, visitor_phone, car_plate_no, visit_time, appointment_status, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("VIS"),
                JdbcMaps.longVal(body, "tenantId"),
                JdbcMaps.requiredStr(body, "Visitor name is required", "visitorName", "name"),
                JdbcMaps.requiredStr(body, "Visitor phone is required", "visitorPhone", "phone"),
                JdbcMaps.str(body, "carPlateNo", "plateNo"),
                JdbcMaps.datetime(body, JdbcMaps.now(), "visitTime", "appointmentTime"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "reason")
        );
        return ApiResponse.success();
    }

    @PatchMapping("/visitors/{id}/enter")
    public ApiResponse<Void> enter(@PathVariable Long id) {
        pass(id, "ENTER");
        updateStatus(id, "ENTERED");
        return ApiResponse.success();
    }

    @PatchMapping("/visitors/{id}/leave")
    public ApiResponse<Void> leave(@PathVariable Long id) {
        pass(id, "LEAVE");
        updateStatus(id, "LEFT");
        return ApiResponse.success();
    }

    @PatchMapping("/visitors/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        updateStatus(id, "CANCELLED");
        return ApiResponse.success();
    }

    private void updateStatus(Long id, String status) {
        int updated = jdbcTemplate.update("UPDATE visitor_appointment SET appointment_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", status, 0L, id);
        if (updated == 0) {
            throw new BusinessException(404, "Visitor appointment not found");
        }
    }

    private void pass(Long id, String type) {
        jdbcTemplate.update(
                "INSERT INTO visitor_pass_record (appointment_id, pass_type, gate_name, operator_id, status, created_by, updated_by) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, type, "front desk", 0L, 0L, 0L
        );
    }
}

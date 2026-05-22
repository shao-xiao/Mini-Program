package com.dehui.property.modules.workorder.controller;

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
public class WorkOrderController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping({"/workorders", "/mobile/workorders"})
    public ApiResponse<List<Map<String, Object>>> workorders() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, tenant_id AS tenantId, room_id AS roomId, title, category, priority,
                       work_status AS status, assignee_id AS assigneeId, remark
                FROM work_order
                WHERE deleted = 0
                ORDER BY updated_at DESC, id DESC
                """
        ));
    }

    @GetMapping("/workorders/assignable-users")
    public ApiResponse<List<Map<String, Object>>> assignableUsers() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT id, username, real_name AS realName FROM sys_user WHERE deleted = 0 AND status = 'ENABLED' ORDER BY id ASC"
        ));
    }

    @PostMapping("/workorders")
    public ApiResponse<Void> create(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO work_order (code, tenant_id, room_id, title, category, priority, work_status, assignee_id, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("WO"),
                JdbcMaps.longVal(body, "tenantId"),
                JdbcMaps.longVal(body, "roomId"),
                JdbcMaps.requiredStr(body, "Work order title is required", "title", "content"),
                JdbcMaps.strOr(body, "REPAIR", "category", "type"),
                JdbcMaps.strOr(body, "NORMAL", "priority"),
                JdbcMaps.longVal(body, "handlerId", "assigneeId"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "description")
        );
        return ApiResponse.success();
    }

    @PatchMapping("/workorders/{id}/assign")
    public ApiResponse<Void> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        update(id, "ASSIGNED", JdbcMaps.longVal(body, "handlerId", "assigneeId"));
        return ApiResponse.success();
    }

    @PatchMapping("/workorders/{id}/start")
    public ApiResponse<Void> start(@PathVariable Long id) {
        update(id, "PROCESSING", null);
        return ApiResponse.success();
    }

    @PatchMapping("/workorders/{id}/complete")
    public ApiResponse<Void> complete(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        update(id, "COMPLETED", null);
        record(id, "COMPLETE", JdbcMaps.str(body, "result", "remark"));
        return ApiResponse.success();
    }

    @PatchMapping("/workorders/{id}/close")
    public ApiResponse<Void> close(@PathVariable Long id) {
        update(id, "CLOSED", null);
        return ApiResponse.success();
    }

    @PostMapping("/workorders/{id}/generate-bill")
    public ApiResponse<Void> generateBill(@PathVariable Long id) {
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

    private void update(Long id, String status, Long assigneeId) {
        int updated = assigneeId == null
                ? jdbcTemplate.update("UPDATE work_order SET work_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", status, 0L, id)
                : jdbcTemplate.update("UPDATE work_order SET work_status = ?, assignee_id = ?, updated_by = ? WHERE id = ? AND deleted = 0", status, assigneeId, 0L, id);
        if (updated == 0) {
            throw new BusinessException(404, "Work order not found");
        }
        record(id, status, null);
    }

    private void record(Long id, String action, String content) {
        jdbcTemplate.update(
                "INSERT INTO work_order_record (work_order_id, operator_id, action, content, status, created_by, updated_by) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, 0L, action, content, 0L, 0L
        );
    }
}

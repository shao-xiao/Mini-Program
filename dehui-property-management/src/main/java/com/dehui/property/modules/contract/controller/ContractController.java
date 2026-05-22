package com.dehui.property.modules.contract.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ContractController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/contracts")
    public ApiResponse<List<Map<String, Object>>> contracts() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT c.id,
                       c.code,
                       c.code AS contractNumber,
                       c.tenant_id AS tenantId,
                       t.name AS tenantName,
                       c.room_id AS roomId,
                       r.room_no AS roomName,
                       c.start_date AS startDate,
                       c.end_date AS endDate,
                       c.rent_amount AS rentAmount,
                       c.deposit_amount AS depositAmount,
                       c.status,
                       c.remark
                FROM contract c
                LEFT JOIN tenant t ON t.id = c.tenant_id
                LEFT JOIN building_room r ON r.id = c.room_id
                WHERE c.deleted = 0
                ORDER BY c.updated_at DESC, c.id DESC
                """
        ));
    }

    @GetMapping("/contracts/pending-checkin")
    public ApiResponse<List<Map<String, Object>>> pendingCheckinContracts() {
        return contracts();
    }

    @PostMapping("/contracts")
    public ApiResponse<Void> createContract(@RequestBody Map<String, Object> body) {
        Long tenantId = JdbcMaps.requiredLong(body, "Tenant is required", "tenantId", "tenant_id");
        Long roomId = JdbcMaps.requiredLong(body, "Room is required", "roomId", "room_id");
        jdbcTemplate.update(
                """
                INSERT INTO contract (code, tenant_id, room_id, start_date, end_date, rent_amount, deposit_amount, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                JdbcMaps.strOr(body, JdbcMaps.code("CON"), "contractNumber", "code"),
                tenantId,
                roomId,
                JdbcMaps.date(body, JdbcMaps.today(), "startDate", "start_date"),
                JdbcMaps.date(body, JdbcMaps.today(), "endDate", "end_date"),
                JdbcMaps.decimal(body, BigDecimal.ZERO, "rentAmount", "rent"),
                JdbcMaps.decimal(body, BigDecimal.ZERO, "depositAmount", "deposit"),
                JdbcMaps.strOr(body, "DRAFT", "status"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "contractName")
        );
        return ApiResponse.success();
    }

    @PostMapping("/contracts/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable Long id) {
        updateStatus(id, "ACTIVE");
        return ApiResponse.success();
    }

    @PostMapping("/contracts/{id}/check-in")
    public ApiResponse<Void> checkIn(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT room_id FROM contract WHERE id = ? AND deleted = 0", id);
        jdbcTemplate.update("UPDATE building_room SET rent_status = 'RENTED', updated_by = ? WHERE id = ?", 0L, row.get("room_id"));
        updateStatus(id, "ACTIVE");
        return ApiResponse.success();
    }

    @PostMapping("/contracts/{id}/terminate")
    public ApiResponse<Void> terminate(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        updateStatus(id, "TERMINATED");
        return ApiResponse.success();
    }

    @PostMapping("/contracts/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        updateStatus(id, "CANCELLED");
        return ApiResponse.success();
    }

    @PostMapping("/contracts/generate-bills")
    public ApiResponse<Map<String, Object>> generateBills() {
        List<Map<String, Object>> activeContracts = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, rent_amount FROM contract WHERE deleted = 0 AND status = 'ACTIVE'"
        );
        int count = 0;
        for (Map<String, Object> contract : activeContracts) {
            jdbcTemplate.update(
                    """
                    INSERT INTO bill (code, tenant_id, contract_id, source_type, source_id, bill_type, amount, paid_amount, due_date, audit_status, pay_status, status, created_by, updated_by)
                    VALUES (?, ?, ?, 'CONTRACT', ?, 'RENT', ?, 0, ?, 'PENDING', 'UNPAID', 'ACTIVE', ?, ?)
                    """,
                    JdbcMaps.code("BIL"),
                    contract.get("tenant_id"),
                    contract.get("id"),
                    contract.get("id"),
                    contract.get("rent_amount"),
                    JdbcMaps.today(),
                    0L,
                    0L
            );
            count++;
        }
        return ApiResponse.success(Map.of("generatedCount", count));
    }

    private void updateStatus(Long id, String status) {
        int updated = jdbcTemplate.update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", status, 0L, id);
        if (updated == 0) {
            throw new BusinessException(404, "Contract not found");
        }
    }
}

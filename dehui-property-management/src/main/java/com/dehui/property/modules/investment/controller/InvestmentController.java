package com.dehui.property.modules.investment.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InvestmentController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping({"/investment/displays", "/investment/contents"})
    public ApiResponse<List<Map<String, Object>>> displays() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, title, content, publish_status AS publishStatus, sort_order AS sortOrder, status, remark
                FROM investment_display
                WHERE deleted = 0
                ORDER BY sort_order ASC, id DESC
                """
        ));
    }

    @PostMapping("/investment/contents")
    public ApiResponse<Void> createContent(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO investment_display (code, title, content, publish_status, sort_order, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("INV"),
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.strOr(body, "DRAFT", "status", "publishStatus"),
                JdbcMaps.intVal(body, 0, "sortOrder"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "summary")
        );
        return ApiResponse.success();
    }

    @PutMapping("/investment/contents/{id}")
    public ApiResponse<Void> updateContent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int updated = jdbcTemplate.update(
                """
                UPDATE investment_display
                SET title = ?, content = ?, publish_status = ?, sort_order = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.strOr(body, "DRAFT", "status", "publishStatus"),
                JdbcMaps.intVal(body, 0, "sortOrder"),
                0L,
                JdbcMaps.str(body, "remark", "summary"),
                id
        );
        ensureUpdated(updated, "Investment content not found");
        return ApiResponse.success();
    }

    @PostMapping("/investment/contents/{id}/publish")
    public ApiResponse<Void> publishContent(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE investment_display SET publish_status = 'PUBLISHED', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Investment content not found");
        return ApiResponse.success();
    }

    @GetMapping("/investment/leads")
    public ApiResponse<List<Map<String, Object>>> leads() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, name, phone, company_name AS companyName, desired_area AS desiredArea,
                       intended_use AS intendedUse, source, lead_status AS status, remark
                FROM investment_lead
                WHERE deleted = 0
                ORDER BY updated_at DESC, id DESC
                """
        ));
    }

    @PatchMapping("/investment/leads/{id}/status")
    public ApiResponse<Void> updateLeadStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ensureUpdated(jdbcTemplate.update("UPDATE investment_lead SET lead_status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                JdbcMaps.strOr(body, "FOLLOWING", "status"), 0L, id), "Investment lead not found");
        return ApiResponse.success();
    }

    @PostMapping("/investment/leads/{id}/convert-tenant")
    public ApiResponse<Void> convertTenant(@PathVariable Long id) {
        Map<String, Object> lead = jdbcTemplate.queryForMap("SELECT * FROM investment_lead WHERE id = ? AND deleted = 0", id);
        jdbcTemplate.update(
                "INSERT INTO tenant (code, name, contact_phone, status, created_by, updated_by, remark) VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)",
                JdbcMaps.code("TEN"), lead.get("company_name") == null ? lead.get("name") : lead.get("company_name"), lead.get("phone"), 0L, 0L, "from lead " + id
        );
        updateLeadStatus(id, Map.of("status", "CONVERTED_TENANT"));
        return ApiResponse.success();
    }

    @PostMapping("/investment/leads/{id}/convert-contract")
    public ApiResponse<Void> convertContract(@PathVariable Long id) {
        updateLeadStatus(id, Map.of("status", "CONVERTED_CONTRACT"));
        return ApiResponse.success();
    }

    @GetMapping("/mobile/investment/overview")
    public ApiResponse<Map<String, Object>> mobileOverview() {
        return ApiResponse.success(Map.of("contents", displays().data(), "leads", List.of()));
    }

    @PostMapping("/mobile/investment/leads")
    public ApiResponse<Void> mobileLead(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO investment_lead (code, name, phone, company_name, desired_area, intended_use, source, lead_status, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, 'MINIPROGRAM', 'NEW', 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("LEAD"),
                JdbcMaps.requiredStr(body, "Name is required", "name"),
                JdbcMaps.requiredStr(body, "Phone is required", "phone"),
                JdbcMaps.str(body, "companyName", "company"),
                JdbcMaps.decimal(body, BigDecimal.ZERO, "desiredArea"),
                JdbcMaps.str(body, "intendedUse"),
                0L,
                0L,
                JdbcMaps.str(body, "remark")
        );
        return ApiResponse.success();
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }
}

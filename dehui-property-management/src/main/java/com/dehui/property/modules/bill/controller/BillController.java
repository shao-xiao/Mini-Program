package com.dehui.property.modules.bill.controller;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BillController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/bills")
    public ApiResponse<List<Map<String, Object>>> bills() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT b.id,
                       b.code,
                       b.code AS billNumber,
                       b.tenant_id AS tenantId,
                       t.name AS tenantName,
                       b.contract_id AS contractId,
                       b.source_type AS sourceType,
                       b.bill_type AS billType,
                       b.amount,
                       b.paid_amount AS paidAmount,
                       b.due_date AS dueDate,
                       b.audit_status AS auditStatus,
                       b.pay_status AS payStatus,
                       b.pay_status AS status,
                       b.remark,
                       CASE WHEN i.id IS NULL THEN 'NONE' ELSE i.invoice_status END AS invoiceStatus
                FROM bill b
                LEFT JOIN tenant t ON t.id = b.tenant_id
                LEFT JOIN bill_invoice i ON i.bill_id = b.id AND i.deleted = 0
                WHERE b.deleted = 0
                ORDER BY b.due_date DESC, b.updated_at DESC, b.id DESC
                """
        ));
    }

    @PostMapping("/bills")
    public ApiResponse<Void> createBill(@RequestBody Map<String, Object> body) {
        Long tenantId = JdbcMaps.requiredLong(body, "Tenant is required", "tenantId", "tenant_id");
        jdbcTemplate.update(
                """
                INSERT INTO bill (code, tenant_id, contract_id, source_type, source_id, bill_type, amount, paid_amount, due_date, audit_status, pay_status, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 'PENDING', 'UNPAID', 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.strOr(body, JdbcMaps.code("BIL"), "billNumber", "code"),
                tenantId,
                JdbcMaps.longVal(body, "contractId", "contract_id"),
                JdbcMaps.strOr(body, "MANUAL", "sourceType"),
                JdbcMaps.longVal(body, "sourceId"),
                JdbcMaps.strOr(body, "OTHER", "billType"),
                JdbcMaps.decimal(body, BigDecimal.ZERO, "amount"),
                JdbcMaps.date(body, JdbcMaps.today(), "dueDate", "due_date"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "title")
        );
        return ApiResponse.success();
    }

    @PostMapping("/bills/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        updateBill(id, "audit_status = 'APPROVED', remark = COALESCE(?, remark)", JdbcMaps.str(body, "auditRemark", "remark"));
        return ApiResponse.success();
    }

    @PostMapping("/bills/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        updateBill(id, "audit_status = 'REJECTED', remark = COALESCE(?, remark)", JdbcMaps.str(body, "auditRemark", "remark"));
        return ApiResponse.success();
    }

    @PostMapping("/bills/{id}/pay")
    public ApiResponse<Void> pay(@PathVariable Long id) {
        Map<String, Object> bill = jdbcTemplate.queryForMap("SELECT tenant_id, amount FROM bill WHERE id = ? AND deleted = 0", id);
        jdbcTemplate.update(
                """
                INSERT INTO bill_payment_record (code, bill_id, tenant_id, pay_channel, amount, paid_at, status, created_by, updated_by)
                VALUES (?, ?, ?, 'OFFLINE', ?, ?, 'PAID', ?, ?)
                """,
                JdbcMaps.code("PAY"),
                id,
                bill.get("tenant_id"),
                bill.get("amount"),
                JdbcMaps.now(),
                0L,
                0L
        );
        updateBill(id, "paid_amount = amount, pay_status = 'PAID'", null);
        return ApiResponse.success();
    }

    @PostMapping("/bills/{id}/invoice")
    public ApiResponse<Void> uploadInvoice(@PathVariable Long id) {
        Map<String, Object> bill = jdbcTemplate.queryForMap("SELECT tenant_id, amount FROM bill WHERE id = ? AND deleted = 0", id);
        jdbcTemplate.update("UPDATE bill_invoice SET deleted = 1 WHERE bill_id = ? AND deleted = 0", id);
        jdbcTemplate.update(
                """
                INSERT INTO bill_invoice (code, bill_id, tenant_id, invoice_title, amount, invoice_status, issued_at, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, 'INVOICED', ?, 'ACTIVE', ?, ?)
                """,
                JdbcMaps.code("INV"),
                id,
                bill.get("tenant_id"),
                "Invoice " + id,
                bill.get("amount"),
                JdbcMaps.now(),
                0L,
                0L
        );
        return ApiResponse.success();
    }

    @DeleteMapping("/bills/{id}/invoice")
    public ApiResponse<Void> deleteInvoice(@PathVariable Long id) {
        jdbcTemplate.update("UPDATE bill_invoice SET deleted = 1, updated_by = ? WHERE bill_id = ? AND deleted = 0", 0L, id);
        return ApiResponse.success();
    }

    @GetMapping("/payments")
    public ApiResponse<List<Map<String, Object>>> payments() {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT * FROM bill_payment_record WHERE deleted = 0 ORDER BY id DESC"));
    }

    @GetMapping("/invoices")
    public ApiResponse<List<Map<String, Object>>> invoices() {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT * FROM bill_invoice WHERE deleted = 0 ORDER BY id DESC"));
    }

    @GetMapping("/mobile/bills")
    public ApiResponse<List<Map<String, Object>>> mobileBills() {
        return bills();
    }

    private void updateBill(Long id, String setClause, Object value) {
        String sql = "UPDATE bill SET " + setClause + ", updated_by = ? WHERE id = ? AND deleted = 0";
        int updated = value == null ? jdbcTemplate.update(sql, 0L, id) : jdbcTemplate.update(sql, value, 0L, id);
        if (updated == 0) {
            throw new BusinessException(404, "Bill not found");
        }
    }
}

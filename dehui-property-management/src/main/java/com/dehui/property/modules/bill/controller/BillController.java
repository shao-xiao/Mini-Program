package com.dehui.property.modules.bill.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BillController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/bills")
    public ApiResponse<List<Map<String, Object>>> bills() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT id, code, tenant_id AS tenantId, contract_id AS contractId, source_type AS sourceType, bill_type AS billType, amount, paid_amount AS paidAmount, due_date AS dueDate, audit_status AS auditStatus, pay_status AS payStatus, status FROM bill WHERE deleted = 0 ORDER BY due_date DESC, updated_at DESC, id DESC"
        ));
    }

    @GetMapping("/payments")
    public ApiResponse<Void> payments() {
        throw BusinessException.notImplemented("payment");
    }

    @GetMapping("/invoices")
    public ApiResponse<Void> invoices() {
        throw BusinessException.notImplemented("invoice");
    }

    @GetMapping("/mobile/bills")
    public ApiResponse<Void> mobileBills() {
        throw BusinessException.notImplemented("mobile bill");
    }
}

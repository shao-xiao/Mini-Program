package com.dehui.property.modules.contract.controller;

import com.dehui.property.common.ApiResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ContractController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/contracts")
    public ApiResponse<List<Map<String, Object>>> contracts() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                "SELECT id, code, tenant_id AS tenantId, room_id AS roomId, start_date AS startDate, end_date AS endDate, rent_amount AS rentAmount, deposit_amount AS depositAmount, status FROM contract WHERE deleted = 0 ORDER BY updated_at DESC, id DESC"
        ));
    }
}

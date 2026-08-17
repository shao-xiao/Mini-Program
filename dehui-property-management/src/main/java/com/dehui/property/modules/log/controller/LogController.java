package com.dehui.property.modules.log.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LogController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/logs/operations")
    public ApiResponse<Void> operationLogs() {
        throw BusinessException.notImplemented("操作日志");
    }

    @GetMapping("/logs/logins")
    public ApiResponse<Map<String, Object>> loginLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        int pageIndex = Math.max(page == null ? 0 : page, 0);
        int pageSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        String where = " WHERE deleted = 0 ";
        List<Object> args = new ArrayList<>();
        if (userId != null) {
            where += " AND user_type = 'SYSTEM' AND user_id = ? ";
            args.add(userId);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM log_login" + where,
                Long.class,
                args.toArray()
        );
        args.add(pageSize);
        args.add(pageIndex * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                """
                SELECT id, code, user_type AS userType, user_id AS userId, username,
                       client_ip AS clientIp, login_result AS loginResult, login_at AS loginAt
                FROM log_login
                """ + where + " ORDER BY login_at DESC, id DESC LIMIT ? OFFSET ?",
                args.toArray()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0L : total);
        result.put("page", pageIndex);
        result.put("size", pageSize);
        return ApiResponse.success(result);
    }
}

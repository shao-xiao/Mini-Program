package com.dehui.property.modules.checkin.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.modules.mobile.controller.MobileApiSupport;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckinController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/checkins")
    public ApiResponse<List<Map<String, Object>>> checkins() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT c.id, c.code, c.sys_user_id AS sysUserId, u.real_name AS staffName,
                       c.checkin_type AS checkinType, c.checkin_time AS checkinTime,
                       c.location, c.longitude, c.latitude, c.status, c.remark
                FROM staff_checkin c
                LEFT JOIN sys_user u ON u.id = c.sys_user_id
                WHERE c.deleted = 0
                ORDER BY c.checkin_time DESC, c.id DESC
                """
        ));
    }

    @GetMapping("/mobile/checkins")
    public ApiResponse<Map<String, Object>> mobileCheckins(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Long sysUserId = MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
        List<Map<String, Object>> checkins = jdbcTemplate.queryForList(
                """
                SELECT id, code, sys_user_id AS sysUserId, checkin_type AS checkinType,
                       checkin_time AS checkinTime, location, longitude, latitude, status, remark
                FROM staff_checkin
                WHERE deleted = 0 AND sys_user_id = ?
                ORDER BY checkin_time DESC, id DESC
                """,
                sysUserId
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", MobileApiSupport.profile(jdbcTemplate, principal));
        response.put("checkins", checkins);
        return ApiResponse.success(response);
    }

    @PostMapping("/mobile/checkins")
    public ApiResponse<Map<String, Object>> mobileCreateCheckin(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        Long sysUserId = MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
        String code = JdbcMaps.code("CHK");
        String checkinTime = JdbcMaps.now();
        jdbcTemplate.update(
                """
                INSERT INTO staff_checkin (code, sys_user_id, checkin_type, checkin_time, location, longitude, latitude, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                code,
                sysUserId,
                JdbcMaps.strOr(body, "ON_DUTY", "checkinType"),
                checkinTime,
                JdbcMaps.str(body, "location"),
                JdbcMaps.decimal(body, null, "longitude"),
                JdbcMaps.decimal(body, null, "latitude"),
                sysUserId,
                sysUserId,
                JdbcMaps.str(body, "remark")
        );
        Long id = jdbcTemplate.queryForObject("SELECT id FROM staff_checkin WHERE code = ? AND deleted = 0 LIMIT 1", Long.class, code);
        return ApiResponse.success(Map.of("id", id, "code", code, "checkinTime", checkinTime));
    }
}

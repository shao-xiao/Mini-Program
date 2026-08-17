package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MobileProfileController {

    private final JdbcTemplate jdbcTemplate;
    private final MobileIdentityService mobileIdentityService;

    @Autowired
    public MobileProfileController(
            JdbcTemplate jdbcTemplate,
            MobileIdentityService mobileIdentityService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mobileIdentityService = mobileIdentityService;
    }

    public MobileProfileController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new MobileIdentityService(jdbcTemplate));
    }

    @GetMapping("/mobile/me")
    public ApiResponse<Map<String, Object>> me(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        AuthPrincipal effectivePrincipal = MobileApiSupport.currentPrincipal(jdbcTemplate, principal.userId())
                .orElse(new AuthPrincipal(
                        principal.userId(),
                        "mobile:" + principal.userId(),
                        "PUBLIC",
                        null,
                        java.util.List.of("MOBILE_USER"),
                        java.util.List.of()
                ));
        String userType = MobileApiSupport.normalizeUserType(effectivePrincipal.userType());
        return ApiResponse.success(MobileApiSupport.me(
                jdbcTemplate,
                effectivePrincipal,
                mobileIdentityService.availableIdentities(effectivePrincipal.userId()),
                mobileIdentityService.bindingState(effectivePrincipal.userId(), userType)
        ));
    }

    @GetMapping("/mobile/mine/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Long tenantId = MobileApiSupport.tenantId(principal);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("announcementCount", count("SELECT COUNT(*) FROM notice WHERE deleted = 0 AND publish_status = 'PUBLISHED'"));
        summary.put("availableRoomCount", count("SELECT COUNT(*) FROM building_room WHERE deleted = 0 AND rent_status IN ('VACANT', 'AVAILABLE')"));
        summary.put("investmentLeadCount", count("SELECT COUNT(*) FROM investment_lead WHERE deleted = 0 AND source = 'MINIPROGRAM' AND created_by = ?", principal.userId()));

        boolean needTenantBind = tenantId == null;
        summary.put("needTenantBind", needTenantBind);
        summary.put("billCount", tenantId == null ? null : count("SELECT COUNT(*) FROM bill WHERE deleted = 0 AND tenant_id = ?", tenantId));
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        summary.put("meetingCount", tenantId == null ? null : count(
                "SELECT COUNT(*) FROM meeting_booking WHERE deleted = 0 AND tenant_id = ? AND (? = 1 OR applicant_mobile_user_id = ?)",
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        ));
        summary.put("workOrderCount", tenantId == null
                ? null
                : count(
                        "SELECT COUNT(*) FROM work_order WHERE deleted = 0 AND tenant_id = ? AND (? = 1 OR submitter_mobile_user_id = ?)",
                        tenantId,
                        companyScope ? 1 : 0,
                        principal.userId()
                ));
        summary.put("visitorCount", tenantId == null ? null : count(
                "SELECT COUNT(*) FROM visitor_appointment WHERE deleted = 0 AND tenant_id = ? AND (? = 1 OR applicant_mobile_user_id = ?)",
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        ));
        return ApiResponse.success(summary);
    }

    private Long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}

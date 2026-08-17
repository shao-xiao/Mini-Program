package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.security.TokenService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MobileStaffIdentityInvalidationTest {

    @Test
    void employeePhoneChangeDisablesBindingExpiresCandidatesAndRevokesEveryMobileSession() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TokenService tokenService = mock(TokenService.class);
        Map<String, Object> binding = eligibleBinding();
        binding.put("staffPhone", "13900001234");
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_user_bind bind_record")),
                any(Object[].class)
        )).thenReturn(List.of(binding));
        when(jdbcTemplate.update(
                argThat((String sql) -> sql != null && sql.contains("UPDATE mobile_user_bind")),
                any(Object[].class)
        )).thenReturn(1);

        int invalidated = new MobileIdentityService(jdbcTemplate, tokenService)
                .invalidateIneligibleStaffBindings(
                        List.of(1L),
                        9L,
                        "员工手机号发生变化"
                );

        assertEquals(1, invalidated);
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null
                        && sql.contains("SET current_bind_id = NULL, user_type = 'PUBLIC'")),
                any(Object[].class)
        );
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null
                        && sql.contains("UPDATE mobile_identity_candidate")
                        && sql.contains("candidate_status = 'EXPIRED'")),
                any(Object[].class)
        );
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null
                        && sql.contains("INSERT INTO mobile_identity_audit")),
                any(Object[].class)
        );
        verify(tokenService).revokeUserSessions(53L);
    }

    @Test
    void stillEligibleWechatVerifiedBindingIsNotInvalidated() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TokenService tokenService = mock(TokenService.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(eligibleBinding()));

        int invalidated = new MobileIdentityService(jdbcTemplate, tokenService)
                .invalidateIneligibleStaffBindings(
                        List.of(1L),
                        9L,
                        "员工资料保存"
                );

        assertEquals(0, invalidated);
        verify(tokenService, never()).revokeUserSessions(any());
    }

    @Test
    void disablingMobileUserUpdatesStatusAndRevokesSessionsInsideIdentityLifecycleService() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TokenService tokenService = mock(TokenService.class);
        when(jdbcTemplate.update(
                argThat((String sql) -> sql != null && sql.startsWith("UPDATE mobile_user SET status")),
                any(Object[].class)
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("SELECT current_bind_id")),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(42L);

        new MobileIdentityService(jdbcTemplate, tokenService)
                .adminUpdateStatus(53L, "DISABLED", 1L);

        verify(jdbcTemplate).update(
                "UPDATE mobile_user SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                "DISABLED",
                1L,
                53L
        );
        verify(tokenService).revokeUserSessions(53L);
    }

    @Test
    void adminIdentitySwitchRevokesEveryPreviouslyIssuedMobileSession() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TokenService tokenService = mock(TokenService.class);
        when(jdbcTemplate.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("SELECT current_bind_id")),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(41L);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_user_bind bind_record")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of("bindId", 42L)));
        when(jdbcTemplate.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("SELECT bind_type")),
                eq(String.class),
                any(Object[].class)
        )).thenReturn("INTERNAL");

        new MobileIdentityService(jdbcTemplate, tokenService)
                .adminSelectIdentity(53L, 42L, 1L);

        verify(tokenService).revokeUserSessions(53L);
    }

    @Test
    void candidateSelectionRevokesOldSessionsBeforeReturningTheSelectedBinding() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TokenService tokenService = mock(TokenService.class);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_identity_candidate")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of("candidateId", 7L, "bindId", 42L)));
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_user_bind bind_record")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of("bindId", 42L)));
        when(jdbcTemplate.update(
                argThat((String sql) -> sql != null
                        && sql.contains("SET candidate_status = 'USED'")),
                any(Object[].class)
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("SELECT bind_type")),
                eq(String.class),
                any(Object[].class)
        )).thenReturn("INTERNAL");

        Long selectedBindId = new MobileIdentityService(jdbcTemplate, tokenService)
                .consumeCandidate(53L, "opaque-candidate-token");

        assertEquals(42L, selectedBindId);
        verify(tokenService).revokeUserSessions(53L);
    }

    private Map<String, Object> eligibleBinding() {
        Map<String, Object> binding = new HashMap<>();
        binding.put("bindId", 42L);
        binding.put("mobileUserId", 53L);
        binding.put("currentBindId", 42L);
        binding.put("phone", "13800001234");
        binding.put("phoneVerifiedAt", LocalDateTime.of(2026, 7, 30, 10, 0));
        binding.put("phoneVerificationSource", "WECHAT_API");
        binding.put("mobileStatus", "ENABLED");
        binding.put("staffPhone", "13800001234");
        binding.put("staffStatus", "ENABLED");
        binding.put("mobileEnabled", 1);
        binding.put("hasMobileWorkbenchAccess", 1);
        return binding;
    }
}

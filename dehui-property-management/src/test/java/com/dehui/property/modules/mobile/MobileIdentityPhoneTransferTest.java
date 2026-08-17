package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MobileIdentityPhoneTransferTest {

    @Test
    void verifiedPhoneMovesProvisionalWechatIdentityToExistingBusinessUser() {
        JdbcTemplate jdbcTemplate = transferFixture(false);
        MobileIdentityService service = new MobileIdentityService(jdbcTemplate);

        MobileIdentityService.Resolution resolution =
                service.verifyPhoneAndResolve(21L, "+86 138-0000-1234");

        assertEquals(9L, resolution.mobileUserId());
        assertEquals("UNMATCHED", resolution.bindingState());
        verify(jdbcTemplate).update(
                argThat(sql -> sql != null && sql.contains("SET mobile_user_id = ?")),
                any(Object[].class)
        );
        verify(jdbcTemplate, times(2)).update(
                argThat(sql -> sql != null && sql.contains("INSERT INTO mobile_identity_audit")),
                any(Object[].class)
        );
    }

    @Test
    void transferIsRejectedWhenCurrentWechatAlreadyOwnsBusinessData() {
        JdbcTemplate jdbcTemplate = transferFixture(true);
        MobileIdentityService service = new MobileIdentityService(jdbcTemplate);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verifyPhoneAndResolve(21L, "13800001234")
        );

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate, never()).update(
                argThat(sql -> sql != null && sql.contains("SET mobile_user_id = ?")),
                any(Object[].class)
        );
    }

    @Test
    void verifiedPhoneClosesOnlyIneligibleLegacyStaffBindingBeforeSafeTransfer() {
        JdbcTemplate jdbcTemplate = transferFixture(false, true);
        MobileIdentityService service = new MobileIdentityService(jdbcTemplate);

        MobileIdentityService.Resolution resolution =
                service.verifyPhoneAndResolve(21L, "13800001234");

        assertEquals(9L, resolution.mobileUserId());
        assertEquals("UNMATCHED", resolution.bindingState());
        verify(jdbcTemplate).update(
                argThat(sql -> sql != null
                        && sql.contains("UPDATE mobile_user_bind")
                        && sql.contains("SET status = 'DISABLED'")),
                any(Object[].class)
        );
        verify(jdbcTemplate).update(
                argThat(sql -> sql != null && sql.contains("SET mobile_user_id = ?")),
                any(Object[].class)
        );
    }

    @Test
    void staleStaffTransferStillRejectsAnyNonBindingBusinessHistory() {
        JdbcTemplate jdbcTemplate = transferFixture(true, true);
        MobileIdentityService service = new MobileIdentityService(jdbcTemplate);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verifyPhoneAndResolve(21L, "13800001234")
        );

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate, never()).update(
                argThat(sql -> sql != null
                        && sql.contains("UPDATE mobile_user_bind")
                        && sql.contains("SET status = 'DISABLED'")),
                any(Object[].class)
        );
        verify(jdbcTemplate, never()).update(
                argThat(sql -> sql != null && sql.contains("SET mobile_user_id = ?")),
                any(Object[].class)
        );
    }

    @Test
    void staleStaffTransferRejectsAnyTenantBindingHistory() {
        JdbcTemplate jdbcTemplate = transferFixture(false, true);
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null
                        && sql.contains("SELECT id, bind_type AS bindType")
                        && sql.contains("FROM mobile_user_bind")),
                any(Object[].class)
        )).thenReturn(List.of(
                Map.of("id", 42L, "bindType", "INTERNAL", "status", "ENABLED"),
                Map.of("id", 43L, "bindType", "TENANT", "status", "DISABLED")
        ));
        MobileIdentityService service = new MobileIdentityService(jdbcTemplate);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verifyPhoneAndResolve(21L, "13800001234")
        );

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate, never()).update(
                argThat(sql -> sql != null
                        && sql.contains("UPDATE mobile_user_bind")
                        && sql.contains("SET status = 'DISABLED'")),
                any(Object[].class)
        );
        verify(jdbcTemplate, never()).update(
                argThat(sql -> sql != null && sql.contains("SET mobile_user_id = ?")),
                any(Object[].class)
        );
    }

    private JdbcTemplate transferFixture(boolean hasBusinessReferences) {
        return transferFixture(hasBusinessReferences, false);
    }

    private JdbcTemplate transferFixture(boolean hasBusinessReferences, boolean staleStaffBinding) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("REGEXP_REPLACE")
                            && sql.contains("id <> ?")
                            && sql.contains("FROM mobile_user")) {
                        return List.of(Map.of(
                                "id", 9L,
                                "status", "ENABLED",
                                "currentBindId", 31L
                        ));
                    }
                    if (sql.contains("phone_verified_at AS phoneVerifiedAt")
                            && sql.contains("FOR UPDATE")) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", 21L);
                        row.put("phone", null);
                        row.put("phoneVerifiedAt", null);
                        row.put("phoneVerificationSource", "UNVERIFIED");
                        row.put("currentBindId", staleStaffBinding ? 42L : null);
                        row.put("userType", staleStaffBinding ? "STAFF" : "PUBLIC");
                        row.put("status", "ENABLED");
                        return List.of(row);
                    }
                    if (sql.contains("SELECT id, bind_type AS bindType")
                            && sql.contains("FROM mobile_user_bind")) {
                        return staleStaffBinding
                                ? List.of(Map.of(
                                        "id", 42L,
                                        "bindType", "INTERNAL",
                                        "status", "ENABLED"
                                ))
                                : List.of();
                    }
                    if (sql.contains("FROM (")
                            && sql.contains("business_reference")) {
                        return hasBusinessReferences
                                ? List.of(Map.of("referenced", 1))
                                : List.of();
                    }
                    if (sql.contains("FROM wechat_identity")
                            && sql.contains("FOR UPDATE")) {
                        return List.of(Map.of("id", 77L, "appId", "wx-app"));
                    }
                    return List.of();
                });
        when(jdbcTemplate.update(
                argThat(sql -> sql != null && sql.contains("SET mobile_user_id = ?")),
                any(Object[].class)
        )).thenReturn(1);
        when(jdbcTemplate.update(
                argThat(sql -> sql != null
                        && sql.contains("UPDATE mobile_user_bind")
                        && sql.contains("SET status = 'DISABLED'")),
                any(Object[].class)
        )).thenReturn(staleStaffBinding ? 1 : 0);
        return jdbcTemplate;
    }
}

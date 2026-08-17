package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.security.TokenService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "dehui.mysql.integration", matches = "true")
class MobileIdentitySessionRotationRollbackMySqlTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MobileIdentityService mobileIdentityService;

    @MockBean
    private TokenService tokenService;

    @Test
    void redisRevocationFailureRollsBackVerifiedPhoneAndIdentityChanges() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String code = "ROLLBACK-PUBLIC-" + suffix;
        String phone = unusedPhone(suffix);
        Long mobileUserId = null;
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO mobile_user
                      (code, nickname, user_type, status, created_by, updated_by, remark)
                    VALUES (?, '会话撤销回滚测试', 'PUBLIC', 'ENABLED', 0, 0, 'Redis故障注入')
                    """,
                    code
            );
            mobileUserId = jdbcTemplate.queryForObject(
                    "SELECT id FROM mobile_user WHERE code = ? AND deleted = 0",
                    Long.class,
                    code
            );
            Long testMobileUserId = mobileUserId;
            doThrow(new IllegalStateException("simulated Redis outage"))
                    .when(tokenService)
                    .revokeUserSessions(testMobileUserId);

            assertThrows(
                    IllegalStateException.class,
                    () -> mobileIdentityService.verifyPhoneAndResolve(testMobileUserId, phone)
            );

            Map<String, Object> persisted = jdbcTemplate.queryForMap(
                    """
                    SELECT phone,
                           phone_verified_at AS phoneVerifiedAt,
                           phone_verification_source AS phoneVerificationSource,
                           current_bind_id AS currentBindId,
                           user_type AS userType
                    FROM mobile_user
                    WHERE id = ?
                    """,
                    testMobileUserId
            );
            assertNull(persisted.get("phone"));
            assertNull(persisted.get("phoneVerifiedAt"));
            assertEquals("UNVERIFIED", persisted.get("phoneVerificationSource"));
            assertNull(persisted.get("currentBindId"));
            assertEquals("PUBLIC", persisted.get("userType"));
            verify(tokenService).revokeUserSessions(testMobileUserId);
        } finally {
            reset(tokenService);
            if (mobileUserId != null) {
                jdbcTemplate.update(
                        "DELETE FROM mobile_identity_audit WHERE mobile_user_id = ?",
                        mobileUserId
                );
                jdbcTemplate.update(
                        "DELETE FROM mobile_identity_candidate WHERE mobile_user_id = ?",
                        mobileUserId
                );
                jdbcTemplate.update(
                        "DELETE FROM mobile_user_bind WHERE mobile_user_id = ?",
                        mobileUserId
                );
                jdbcTemplate.update(
                        "DELETE FROM wechat_identity WHERE mobile_user_id = ?",
                        mobileUserId
                );
                jdbcTemplate.update("DELETE FROM mobile_user WHERE id = ?", mobileUserId);
            }
        }
    }

    private String unusedPhone(String suffix) {
        long seed = Integer.toUnsignedLong(suffix.hashCode());
        for (int attempt = 0; attempt < 100; attempt++) {
            String phone = "197" + String.format(
                    Locale.ROOT,
                    "%08d",
                    (seed + attempt) % 100_000_000L
            );
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT
                      (SELECT COUNT(*) FROM mobile_user WHERE phone = ? AND deleted = 0)
                      + (SELECT COUNT(*) FROM tenant_contact WHERE phone = ? AND deleted = 0)
                      + (SELECT COUNT(*) FROM sys_user WHERE phone = ? AND deleted = 0)
                    """,
                    Integer.class,
                    phone,
                    phone,
                    phone
            );
            if (count != null && count == 0) {
                return phone;
            }
        }
        throw new IllegalStateException("无法生成隔离测试手机号");
    }
}

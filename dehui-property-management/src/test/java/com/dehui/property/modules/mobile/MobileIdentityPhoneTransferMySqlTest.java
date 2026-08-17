package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dehui.property.modules.mobile.service.MobileIdentityService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@EnabledIfSystemProperty(named = "dehui.mysql.integration", matches = "true")
class MobileIdentityPhoneTransferMySqlTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MobileIdentityService mobileIdentityService;

    @Test
    void verifiedPhoneTransferPreservesBusinessUserAndReplacesWechatIdentity() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String phone = "199" + String.format(
                Locale.ROOT,
                "%08d",
                Integer.toUnsignedLong(suffix.hashCode()) % 100_000_000L
        );
        String ownerCode = "IT-OWNER-" + suffix;
        String provisionalCode = "IT-PUBLIC-" + suffix;
        String appId = "wx-it-" + suffix;

        jdbcTemplate.update(
                """
                INSERT INTO mobile_user
                  (code, nickname, phone, phone_verified_at, user_type, status,
                   created_by, updated_by, remark)
                VALUES (?, '集成测试原业务用户', ?, NOW(), 'PUBLIC', 'ENABLED', 0, 0, '事务回滚测试')
                """,
                ownerCode,
                phone
        );
        jdbcTemplate.update(
                """
                INSERT INTO mobile_user
                  (code, nickname, user_type, status, created_by, updated_by, remark)
                VALUES (?, '集成测试临时用户', 'PUBLIC', 'ENABLED', 0, 0, '事务回滚测试')
                """,
                provisionalCode
        );
        Long ownerId = mobileUserId(ownerCode);
        Long provisionalId = mobileUserId(provisionalCode);
        jdbcTemplate.update(
                """
                INSERT INTO wechat_identity
                  (mobile_user_id, app_id, openid, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, 'ENABLED', 0, 0, '旧微信身份')
                """,
                ownerId,
                appId,
                "old-" + suffix
        );
        jdbcTemplate.update(
                """
                INSERT INTO wechat_identity
                  (mobile_user_id, app_id, openid, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, 'ENABLED', 0, 0, '当前微信身份')
                """,
                provisionalId,
                appId,
                "current-" + suffix
        );

        MobileIdentityService.Resolution resolution =
                mobileIdentityService.verifyPhoneAndResolve(provisionalId, phone);

        assertEquals(ownerId, resolution.mobileUserId());
        assertEquals(
                ownerId,
                jdbcTemplate.queryForObject(
                        "SELECT mobile_user_id FROM wechat_identity WHERE app_id = ? AND openid = ?",
                        Long.class,
                        appId,
                        "current-" + suffix
                )
        );
        assertEquals(
                "DISABLED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM wechat_identity WHERE app_id = ? AND openid = ?",
                        String.class,
                        appId,
                        "old-" + suffix
                )
        );
        assertEquals(
                "DISABLED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM mobile_user WHERE id = ?",
                        String.class,
                        provisionalId
                )
        );
        assertEquals(
                List.of(
                        "WECHAT_PHONE_IDENTITY_TRANSFER_OUT",
                        "WECHAT_PHONE_IDENTITY_TRANSFER_IN"
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT action
                        FROM mobile_identity_audit
                        WHERE mobile_user_id IN (?, ?)
                          AND action LIKE 'WECHAT_PHONE_IDENTITY_TRANSFER_%'
                        ORDER BY id
                        """,
                        String.class,
                        provisionalId,
                        ownerId
                )
        );
    }

    @Test
    void verifiedPhoneSafelyClosesLegacyStaffShellBeforeCanonicalTransfer() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String phone = "196" + String.format(
                Locale.ROOT,
                "%08d",
                Integer.toUnsignedLong(suffix.hashCode()) % 100_000_000L
        );
        String ownerCode = "IT-STALE-OWNER-" + suffix;
        String staleCode = "IT-STALE-STAFF-" + suffix;
        String sysUserCode = "IT-STALE-SYS-" + suffix;
        String appId = "wx-stale-" + suffix;

        jdbcTemplate.update(
                """
                INSERT INTO mobile_user
                  (code, nickname, phone, phone_verified_at, user_type, status,
                   created_by, updated_by, remark)
                VALUES (?, '集成测试手机号原用户', ?, NOW(), 'PUBLIC', 'ENABLED', 0, 0, '失效员工绑定收口测试')
                """,
                ownerCode,
                phone
        );
        jdbcTemplate.update(
                """
                INSERT INTO mobile_user
                  (code, nickname, user_type, status, created_by, updated_by, remark)
                VALUES (?, '集成测试失效员工壳账号', 'STAFF', 'ENABLED', 0, 0, '失效员工绑定收口测试')
                """,
                staleCode
        );
        jdbcTemplate.update(
                """
                INSERT INTO sys_user
                  (code, username, password_hash, real_name, status, mobile_enabled,
                   web_login_enabled, created_by, updated_by, remark)
                VALUES (?, ?, '{noop}not-used', '失效绑定测试员工', 'ENABLED', 1, 0, 0, 0, '失效员工绑定收口测试')
                """,
                sysUserCode,
                "it-stale-" + suffix
        );

        Long ownerId = mobileUserId(ownerCode);
        Long staleMobileUserId = mobileUserId(staleCode);
        Long sysUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE code = ? AND deleted = 0",
                Long.class,
                sysUserCode
        );
        jdbcTemplate.update(
                """
                INSERT INTO mobile_user_bind
                  (mobile_user_id, bind_type, sys_user_id, status, created_by, updated_by, remark)
                VALUES (?, 'INTERNAL', ?, 'ENABLED', 0, 0, '历史错误员工绑定')
                """,
                staleMobileUserId,
                sysUserId
        );
        Long staleBindId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM mobile_user_bind
                WHERE mobile_user_id = ? AND sys_user_id = ? AND deleted = 0
                """,
                Long.class,
                staleMobileUserId,
                sysUserId
        );
        jdbcTemplate.update(
                "UPDATE mobile_user SET current_bind_id = ? WHERE id = ?",
                staleBindId,
                staleMobileUserId
        );
        jdbcTemplate.update(
                """
                INSERT INTO wechat_identity
                  (mobile_user_id, app_id, openid, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, 'ENABLED', 0, 0, '手机号原用户旧微信身份')
                """,
                ownerId,
                appId,
                "old-" + suffix
        );
        jdbcTemplate.update(
                """
                INSERT INTO wechat_identity
                  (mobile_user_id, app_id, openid, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, 'ENABLED', 0, 0, '当前微信失效员工身份')
                """,
                staleMobileUserId,
                appId,
                "current-" + suffix
        );

        MobileIdentityService.Resolution resolution =
                mobileIdentityService.verifyPhoneAndResolve(staleMobileUserId, phone);

        assertEquals(ownerId, resolution.mobileUserId());
        assertEquals(
                "DISABLED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM mobile_user_bind WHERE id = ?",
                        String.class,
                        staleBindId
                )
        );
        assertEquals(
                ownerId,
                jdbcTemplate.queryForObject(
                        "SELECT mobile_user_id FROM wechat_identity WHERE app_id = ? AND openid = ?",
                        Long.class,
                        appId,
                        "current-" + suffix
                )
        );
        assertEquals(
                List.of(
                        "WECHAT_PHONE_STALE_STAFF_CLOSED",
                        "WECHAT_PHONE_IDENTITY_TRANSFER_OUT",
                        "WECHAT_PHONE_IDENTITY_TRANSFER_IN"
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT action
                        FROM mobile_identity_audit
                        WHERE mobile_user_id IN (?, ?)
                          AND action IN (
                            'WECHAT_PHONE_STALE_STAFF_CLOSED',
                            'WECHAT_PHONE_IDENTITY_TRANSFER_OUT',
                            'WECHAT_PHONE_IDENTITY_TRANSFER_IN'
                          )
                        ORDER BY id
                        """,
                        String.class,
                        staleMobileUserId,
                        ownerId
                )
        );
    }

    private Long mobileUserId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM mobile_user WHERE code = ? AND deleted = 0",
                Long.class,
                code
        );
    }
}

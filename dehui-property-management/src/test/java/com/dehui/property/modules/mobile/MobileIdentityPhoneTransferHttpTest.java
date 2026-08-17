package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.mobile.service.WechatMiniProgramClient;
import com.dehui.property.security.AuthPrincipal;
import com.dehui.property.security.TokenService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "dehui.mysql.integration", matches = "true")
class MobileIdentityPhoneTransferHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private WechatMiniProgramClient wechatMiniProgramClient;

    @Test
    void phoneLoginReturnsCanonicalUserTokenAndInvalidatesBothPreviousSessions() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String phone = "198" + String.format(
                Locale.ROOT,
                "%08d",
                Integer.toUnsignedLong(suffix.hashCode()) % 100_000_000L
        );
        String ownerCode = "HTTP-OWNER-" + suffix;
        String provisionalCode = "HTTP-PUBLIC-" + suffix;
        String appId = "wx-http-" + suffix;
        Long ownerId = null;
        Long provisionalId = null;
        String ownerOldToken = null;
        String provisionalToken = null;
        String returnedToken = null;
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO mobile_user
                      (code, nickname, phone, phone_verified_at, user_type, status,
                       created_by, updated_by, remark)
                    VALUES (?, 'HTTP原业务用户', ?, NOW(), 'PUBLIC', 'ENABLED', 0, 0, 'HTTP隔离测试')
                    """,
                    ownerCode,
                    phone
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO mobile_user
                      (code, nickname, user_type, status, created_by, updated_by, remark)
                    VALUES (?, 'HTTP临时用户', 'PUBLIC', 'ENABLED', 0, 0, 'HTTP隔离测试')
                    """,
                    provisionalCode
            );
            ownerId = mobileUserId(ownerCode);
            provisionalId = mobileUserId(provisionalCode);
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

            ownerOldToken = tokenService.createToken(publicPrincipal(ownerId));
            provisionalToken = tokenService.createToken(publicPrincipal(provisionalId));
            when(wechatMiniProgramClient.getPhoneNumber("mock-official-code"))
                    .thenReturn(new WechatMiniProgramClient.PhoneNumber(phone, phone, "86"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", provisionalToken);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "http://127.0.0.1:" + port + "/api/mobile/auth/wechat-phone-login",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("phoneCode", "mock-official-code"), headers),
                    Map.class
            );

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            returnedToken = String.valueOf(data.get("token"));
            assertEquals(
                    ownerId,
                    tokenService.readToken(returnedToken).orElseThrow().userId()
            );
            assertFalse(tokenService.readToken(ownerOldToken).isPresent());
            assertFalse(tokenService.readToken(provisionalToken).isPresent());
            assertEquals(
                    ownerId,
                    jdbcTemplate.queryForObject(
                            "SELECT mobile_user_id FROM wechat_identity WHERE app_id = ? AND openid = ?",
                            Long.class,
                            appId,
                            "current-" + suffix
                    )
            );
        } finally {
            if (returnedToken != null) tokenService.revoke(returnedToken);
            if (ownerId != null) tokenService.revokeUserSessions(ownerId);
            if (provisionalId != null) tokenService.revokeUserSessions(provisionalId);
            if (ownerId != null && provisionalId != null) {
                jdbcTemplate.update(
                        "DELETE FROM mobile_identity_audit WHERE mobile_user_id IN (?, ?)",
                        ownerId,
                        provisionalId
                );
                jdbcTemplate.update(
                        "DELETE FROM mobile_identity_candidate WHERE mobile_user_id IN (?, ?)",
                        ownerId,
                        provisionalId
                );
                jdbcTemplate.update(
                        "DELETE FROM wechat_identity WHERE mobile_user_id IN (?, ?)",
                        ownerId,
                        provisionalId
                );
                jdbcTemplate.update(
                        "DELETE FROM mobile_user_bind WHERE mobile_user_id IN (?, ?)",
                        ownerId,
                        provisionalId
                );
                jdbcTemplate.update(
                        "DELETE FROM mobile_user WHERE id IN (?, ?)",
                        ownerId,
                        provisionalId
                );
            }
        }
    }

    @Test
    void phoneLoginRecoversPublicSessionBackedByIneligibleLegacyStaffBinding() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String phone = "195" + String.format(
                Locale.ROOT,
                "%08d",
                Integer.toUnsignedLong(suffix.hashCode()) % 100_000_000L
        );
        String ownerCode = "HTTP-STALE-OWNER-" + suffix;
        String staleCode = "HTTP-STALE-STAFF-" + suffix;
        String sysUserCode = "HTTP-STALE-SYS-" + suffix;
        String appId = "wx-http-stale-" + suffix;
        Long ownerId = null;
        Long staleMobileUserId = null;
        Long sysUserId = null;
        String staleToken = null;
        String returnedToken = null;
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO mobile_user
                      (code, nickname, phone, phone_verified_at, user_type, status,
                       created_by, updated_by, remark)
                    VALUES (?, 'HTTP手机号原用户', ?, NOW(), 'PUBLIC', 'ENABLED', 0, 0, 'HTTP失效员工绑定测试')
                    """,
                    ownerCode,
                    phone
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO mobile_user
                      (code, nickname, user_type, status, created_by, updated_by, remark)
                    VALUES (?, 'HTTP失效员工壳账号', 'STAFF', 'ENABLED', 0, 0, 'HTTP失效员工绑定测试')
                    """,
                    staleCode
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO sys_user
                      (code, username, password_hash, real_name, status, mobile_enabled,
                       web_login_enabled, created_by, updated_by, remark)
                    VALUES (?, ?, '{noop}not-used', 'HTTP失效绑定员工', 'ENABLED', 1, 0, 0, 0, 'HTTP失效员工绑定测试')
                    """,
                    sysUserCode,
                    "http-stale-" + suffix
            );
            ownerId = mobileUserId(ownerCode);
            staleMobileUserId = mobileUserId(staleCode);
            sysUserId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_user WHERE code = ? AND deleted = 0",
                    Long.class,
                    sysUserCode
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO mobile_user_bind
                      (mobile_user_id, bind_type, sys_user_id, status, created_by, updated_by, remark)
                    VALUES (?, 'INTERNAL', ?, 'ENABLED', 0, 0, 'HTTP历史错误员工绑定')
                    """,
                    staleMobileUserId,
                    sysUserId
            );
            Long staleBindId = jdbcTemplate.queryForObject(
                    """
                    SELECT id FROM mobile_user_bind
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
                    VALUES (?, ?, ?, 'ENABLED', 0, 0, 'HTTP手机号原用户旧微信身份')
                    """,
                    ownerId,
                    appId,
                    "old-" + suffix
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO wechat_identity
                      (mobile_user_id, app_id, openid, status, created_by, updated_by, remark)
                    VALUES (?, ?, ?, 'ENABLED', 0, 0, 'HTTP当前微信失效员工身份')
                    """,
                    staleMobileUserId,
                    appId,
                    "current-" + suffix
            );

            staleToken = tokenService.createToken(publicPrincipal(staleMobileUserId));
            when(wechatMiniProgramClient.getPhoneNumber("mock-stale-official-code"))
                    .thenReturn(new WechatMiniProgramClient.PhoneNumber(phone, phone, "86"));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", staleToken);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "http://127.0.0.1:" + port + "/api/mobile/auth/wechat-phone-login",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("phoneCode", "mock-stale-official-code"), headers),
                    Map.class
            );

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            returnedToken = String.valueOf(data.get("token"));
            assertEquals(ownerId, tokenService.readToken(returnedToken).orElseThrow().userId());
            assertFalse(tokenService.readToken(staleToken).isPresent());
            assertEquals(
                    "DISABLED",
                    jdbcTemplate.queryForObject(
                            "SELECT status FROM mobile_user_bind WHERE id = ?",
                            String.class,
                            staleBindId
                    )
            );
        } finally {
            if (returnedToken != null) tokenService.revoke(returnedToken);
            if (ownerId != null) tokenService.revokeUserSessions(ownerId);
            if (staleMobileUserId != null) tokenService.revokeUserSessions(staleMobileUserId);
            if (ownerId != null && staleMobileUserId != null) {
                jdbcTemplate.update(
                        "DELETE FROM mobile_identity_audit WHERE mobile_user_id IN (?, ?)",
                        ownerId,
                        staleMobileUserId
                );
                jdbcTemplate.update(
                        "DELETE FROM mobile_identity_candidate WHERE mobile_user_id IN (?, ?)",
                        ownerId,
                        staleMobileUserId
                );
                jdbcTemplate.update(
                        "DELETE FROM wechat_identity WHERE mobile_user_id IN (?, ?)",
                        ownerId,
                        staleMobileUserId
                );
                jdbcTemplate.update(
                        "DELETE FROM mobile_user_bind WHERE mobile_user_id IN (?, ?)",
                        ownerId,
                        staleMobileUserId
                );
                jdbcTemplate.update(
                        "DELETE FROM mobile_user WHERE id IN (?, ?)",
                        ownerId,
                        staleMobileUserId
                );
            }
            if (sysUserId != null) {
                jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", sysUserId);
            }
        }
    }

    private AuthPrincipal publicPrincipal(Long userId) {
        return new AuthPrincipal(
                userId,
                "mobile:" + userId,
                "PUBLIC",
                null,
                List.of("MOBILE_USER"),
                List.of()
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

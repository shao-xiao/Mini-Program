package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.mobile.controller.MobileAuthController;
import com.dehui.property.modules.mobile.dto.WechatLoginRequest;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.modules.mobile.service.WechatMiniProgramClient;
import com.dehui.property.security.AuthPrincipal;
import com.dehui.property.security.TokenService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MobileAuthControllerTest {

    @Test
    void newWechatSessionCreatesPublicTechnicalIdentity() {
        Fixture fixture = fixture();
        when(fixture.wechat.exchangeCode("wx-code"))
                .thenReturn(new WechatMiniProgramClient.MiniProgramSession(
                        "appid", "openid", null, "session-hash"
                ));
        when(fixture.jdbc.queryForList(
                argThat(sql -> sql != null && sql.contains("FROM wechat_identity")),
                any(Object[].class)
        )).thenReturn(List.of());
        when(fixture.jdbc.queryForObject(
                argThat(sql -> sql != null && sql.contains("FROM mobile_user WHERE code")),
                org.mockito.ArgumentMatchers.eq(Long.class),
                any(Object[].class)
        )).thenReturn(21L);
        stubIdentity(fixture.jdbc, publicIdentity(21L, null));
        when(fixture.identity.availableIdentities(21L)).thenReturn(List.of());
        when(fixture.identity.bindingState(21L, "PUBLIC")).thenReturn("UNVERIFIED");
        when(fixture.token.createToken(any(AuthPrincipal.class))).thenReturn("public-token");

        ApiResponse<Map<String, Object>> response =
                fixture.controller.wechatLogin(new WechatLoginRequest("wx-code", null));

        assertEquals("PUBLIC", response.data().get("userType"));
        assertEquals("UNVERIFIED", response.data().get("bindingState"));
        assertEquals(List.of("announcement.view", "investment.view", "investment.booking.create", "meeting.public.view"),
                response.data().get("capabilities"));
    }

    @Test
    void publicOnlyWechatSessionDoesNotRestoreBoundTenant() {
        Fixture fixture = fixture();
        when(fixture.wechat.exchangeCode("wx-code"))
                .thenReturn(new WechatMiniProgramClient.MiniProgramSession(
                        "appid", "openid", null, "session-hash"
                ));
        when(fixture.jdbc.queryForList(
                argThat(sql -> sql != null && sql.contains("FROM wechat_identity")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of("mobileUserId", 21L, "identityStatus", "ENABLED")));
        stubIdentity(fixture.jdbc, tenantIdentity(21L));
        when(fixture.identity.bindingState(21L, "PUBLIC")).thenReturn("UNMATCHED");
        when(fixture.token.createToken(any(AuthPrincipal.class))).thenReturn("public-token");

        ApiResponse<Map<String, Object>> response =
                fixture.controller.wechatLogin(new WechatLoginRequest("wx-code", null, false));

        assertEquals("PUBLIC", response.data().get("userType"));
        assertEquals(false, response.data().get("tenantBound"));
        verify(fixture.token).createToken(argThat(principal ->
                "PUBLIC".equals(principal.userType()) && principal.tenantId() == null
        ));
    }

    @Test
    void phoneLoginUsesOfficialPhoneCodeAndIgnoresForgedRelationshipIds() {
        Fixture fixture = fixture();
        AuthPrincipal publicPrincipal = publicPrincipal(21L);
        when(fixture.wechat.getPhoneNumber("official-code"))
                .thenReturn(new WechatMiniProgramClient.PhoneNumber(
                        "13800001234", "13800001234", "86"
                ));
        when(fixture.identity.verifyPhoneAndResolve(21L, "13800001234"))
                .thenReturn(new MobileIdentityService.Resolution("UNMATCHED", List.of()));
        stubIdentity(fixture.jdbc, publicIdentity(21L, "13800001234"));
        when(fixture.token.createToken(any(AuthPrincipal.class))).thenReturn("public-token");

        ApiResponse<Map<String, Object>> response = fixture.controller.wechatPhoneLogin(
                publicPrincipal,
                Map.of(
                        "phoneCode", "official-code",
                        "tenantId", 999L,
                        "tenantContactId", 998L,
                        "sysUserId", 997L
                )
        );

        assertEquals("UNMATCHED", response.data().get("bindingState"));
        @SuppressWarnings("unchecked")
        List<String> capabilities = (List<String>) response.data().get("capabilities");
        assertTrue(capabilities.contains("meeting.guest.book"));
        verify(fixture.identity).verifyPhoneAndResolve(21L, "13800001234");
        verify(fixture.token, never()).revokeUserSessions(any());
        verify(fixture.token).createToken(any(AuthPrincipal.class));
    }

    @Test
    void phoneLoginSwitchesToExistingBusinessUserAfterServiceRotatesOldSessions() {
        Fixture fixture = fixture();
        AuthPrincipal provisionalPrincipal = publicPrincipal(21L);
        when(fixture.wechat.getPhoneNumber("official-code"))
                .thenReturn(new WechatMiniProgramClient.PhoneNumber(
                        "13800001234", "13800001234", "86"
                ));
        when(fixture.identity.verifyPhoneAndResolve(21L, "13800001234"))
                .thenReturn(new MobileIdentityService.Resolution(9L, "BOUND", List.of()));
        stubIdentity(fixture.jdbc, tenantIdentity(9L));
        when(fixture.token.createToken(any(AuthPrincipal.class))).thenReturn("migrated-token");

        ApiResponse<Map<String, Object>> response = fixture.controller.wechatPhoneLogin(
                provisionalPrincipal,
                Map.of("phoneCode", "official-code")
        );

        assertEquals("TENANT", response.data().get("userType"));
        assertEquals("migrated-token", response.data().get("token"));
        verify(fixture.token, never()).revokeUserSessions(any());
        verify(fixture.token).createToken(argThat(principal -> principal.userId().equals(9L)));
    }

    @Test
    void selectingIdentityAcceptsCandidateTokenOnly() {
        Fixture fixture = fixture();
        when(fixture.identity.consumeCandidate(21L, "opaque-token")).thenReturn(33L);
        when(fixture.identity.availableIdentities(21L)).thenReturn(List.of());
        when(fixture.identity.bindingState(21L, "TENANT")).thenReturn("BOUND");
        stubIdentity(fixture.jdbc, tenantIdentity(21L));
        when(fixture.token.createToken(any(AuthPrincipal.class))).thenReturn("tenant-token");

        ApiResponse<Map<String, Object>> response = fixture.controller.selectIdentity(
                publicPrincipal(21L),
                Map.of("candidateToken", "opaque-token")
        );

        assertEquals("TENANT", response.data().get("userType"));
        verify(fixture.identity).consumeCandidate(21L, "opaque-token");
        verify(fixture.token, never()).revokeUserSessions(any());
    }

    @Test
    void accountPasswordBindingEndpointsAreGone() {
        Fixture fixture = fixture();
        BusinessException exception = assertThrows(
                BusinessException.class,
                fixture.controller::deprecatedPasswordBinding
        );
        assertEquals(410, exception.getCode());
    }

    @Test
    void mobileMeDoesNotExposeRawRelationshipIdsOrFullPhone() {
        Fixture fixture = fixture();
        stubIdentity(fixture.jdbc, tenantIdentity(21L));
        when(fixture.identity.availableIdentities(21L)).thenReturn(List.of());
        when(fixture.identity.bindingState(21L, "TENANT")).thenReturn("BOUND");

        Map<String, Object> me = fixture.controller.me(tenantPrincipal(21L)).data();
        assertFalse(me.containsKey("tenantId"));
        assertFalse(me.containsKey("tenantContactId"));
        assertFalse(me.containsKey("sysUserId"));
        assertFalse(me.containsKey("mobileUserId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) me.get("profile");
        assertEquals("138****1234", profile.get("maskedPhone"));
        assertFalse(profile.containsKey("phone"));
    }

    @Test
    void internalInputIsNormalizedToStaffOutput() {
        assertEquals("STAFF",
                com.dehui.property.modules.mobile.controller.MobileApiSupport.normalizeUserType("INTERNAL"));
    }

    @Test
    void unbindReturnsPublicUnmatchedSession() {
        Fixture fixture = fixture();
        stubIdentity(fixture.jdbc, publicIdentity(21L, "13800001234"));
        when(fixture.token.createToken(any(AuthPrincipal.class))).thenReturn("public-token");

        ApiResponse<Map<String, Object>> response =
                fixture.controller.unbindCurrent(tenantPrincipal(21L));

        verify(fixture.identity).unbindCurrent(21L);
        verify(fixture.token, never()).revokeUserSessions(any());
        assertEquals("PUBLIC", response.data().get("userType"));
        assertEquals("UNMATCHED", response.data().get("bindingState"));
    }

    private Fixture fixture() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TokenService token = mock(TokenService.class);
        WechatMiniProgramClient wechat = mock(WechatMiniProgramClient.class);
        MobileIdentityService identity = mock(MobileIdentityService.class);
        return new Fixture(
                jdbc,
                token,
                wechat,
                identity,
                new MobileAuthController(jdbc, token, wechat, identity)
        );
    }

    private void stubIdentity(JdbcTemplate jdbc, Map<String, Object> row) {
        when(jdbc.queryForList(
                argThat(sql -> sql != null && sql.contains("FROM mobile_user mobile")),
                any(Object[].class)
        )).thenReturn(List.of(row));
    }

    private Map<String, Object> publicIdentity(Long id, String phone) {
        Map<String, Object> row = new HashMap<>();
        row.put("mobileUserId", id);
        row.put("nickname", "微信用户");
        row.put("phone", phone);
        if (phone != null) {
            row.put("phoneVerifiedAt", LocalDateTime.of(2026, 7, 28, 10, 0));
        }
        row.put("declaredUserType", "PUBLIC");
        return row;
    }

    private Map<String, Object> tenantIdentity(Long id) {
        Map<String, Object> row = publicIdentity(id, "13800001234");
        row.put("declaredUserType", "TENANT");
        row.put("currentBindType", "TENANT");
        row.put("tenantId", 6L);
        row.put("tenantContactId", 18L);
        row.put("tenantStatus", "ACTIVE");
        row.put("contactStatus", "ACTIVE");
        row.put("contactPhone", "13800001234");
        row.put("contactRole", "RESPONSIBLE");
        row.put("contactName", "张三");
        row.put("boundTenantName", "测试企业");
        return row;
    }

    private AuthPrincipal publicPrincipal(Long id) {
        return new AuthPrincipal(id, "mobile:" + id, "PUBLIC", null,
                List.of("MOBILE_USER"), List.of());
    }

    private AuthPrincipal tenantPrincipal(Long id) {
        return new AuthPrincipal(id, "mobile:" + id, "TENANT", 6L,
                List.of("MOBILE_USER", "MOBILE_TENANT"),
                List.of("announcement.view", "workorder.view"));
    }

    private record Fixture(
            JdbcTemplate jdbc,
            TokenService token,
            WechatMiniProgramClient wechat,
            MobileIdentityService identity,
            MobileAuthController controller) {
    }
}

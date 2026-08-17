package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.modules.mobile.dto.WechatLoginRequest;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.modules.mobile.service.WechatMiniProgramClient;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import com.dehui.property.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mobile/auth")
public class MobileAuthController {

    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final WechatMiniProgramClient wechatMiniProgramClient;
    private final MobileIdentityService mobileIdentityService;

    @Autowired
    public MobileAuthController(
            JdbcTemplate jdbcTemplate,
            TokenService tokenService,
            WechatMiniProgramClient wechatMiniProgramClient,
            MobileIdentityService mobileIdentityService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
        this.wechatMiniProgramClient = wechatMiniProgramClient;
        this.mobileIdentityService = mobileIdentityService;
    }

    /**
     * Kept for focused unit tests compiled against the previous constructor.
     * Password binding is no longer part of the mobile login flow.
     */
    public MobileAuthController(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder ignoredPasswordEncoder,
            TokenService tokenService,
            WechatMiniProgramClient wechatMiniProgramClient) {
        this(jdbcTemplate, tokenService, wechatMiniProgramClient, new MobileIdentityService(jdbcTemplate));
    }

    @Transactional
    @PostMapping("/wechat-login")
    public ApiResponse<Map<String, Object>> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        WechatMiniProgramClient.MiniProgramSession wechatSession =
                wechatMiniProgramClient.exchangeCode(request.code());
        Long mobileUserId = findOrCreateMobileUser(wechatSession);
        mobileIdentityService.touchLogin(mobileUserId);
        return ApiResponse.success(request.shouldRestoreBoundIdentity()
                ? sessionFor(mobileUserId)
                : publicSessionFor(mobileUserId));
    }

    @PostMapping("/wechat-phone-login")
    public ApiResponse<Map<String, Object>> wechatPhoneLogin(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        String phoneCode = JdbcMaps.requiredStr(body, "手机号授权code不能为空", "phoneCode", "code");
        WechatMiniProgramClient.PhoneNumber phoneNumber =
                wechatMiniProgramClient.getPhoneNumber(phoneCode);
        MobileIdentityService.Resolution resolution =
                mobileIdentityService.verifyPhoneAndResolve(principal.userId(), phoneNumber.phoneNumber());
        Long resolvedMobileUserId = resolution.mobileUserId() == null
                ? principal.userId()
                : resolution.mobileUserId();
        Map<String, Object> session = sessionFor(
                resolvedMobileUserId,
                resolution.bindingState(),
                resolution.availableIdentities()
        );
        if ("UNMATCHED".equals(resolution.bindingState())) {
            session.put(
                    "bindingMessage",
                    "手机号已验证，但尚未匹配租户联系人或物业员工档案，请联系管理员维护资料。"
            );
        }
        return ApiResponse.success(session);
    }

    @PostMapping("/select-identity")
    public ApiResponse<Map<String, Object>> selectIdentity(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        String candidateToken = JdbcMaps.requiredStr(body, "candidateToken不能为空", "candidateToken");
        mobileIdentityService.consumeCandidate(principal.userId(), candidateToken);
        return ApiResponse.success(sessionFor(principal.userId()));
    }

    @PostMapping("/unbind-current")
    public ApiResponse<Map<String, Object>> unbindCurrent(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        mobileIdentityService.unbindCurrent(principal.userId());
        return ApiResponse.success(sessionFor(principal.userId(), "UNMATCHED", List.of()));
    }

    /**
     * Old password-based account binding is intentionally closed. Existing
     * clients receive an explicit migration response instead of a silent bind.
     */
    @Deprecated
    @PostMapping({"/bind-internal", "/bind-tenant"})
    public ApiResponse<Void> deprecatedPasswordBinding() {
        throw new BusinessException(410, "账号密码绑定已停用，请使用微信手机号快捷登录");
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        return ApiResponse.success(meFor(principal, null, null));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        tokenService.revoke(request.getHeader("Authorization"));
        return ApiResponse.success();
    }

    private Long findOrCreateMobileUser(WechatMiniProgramClient.MiniProgramSession wechatSession) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                """
                SELECT mobile.id AS mobileUserId, identity.status AS identityStatus
                FROM wechat_identity identity
                INNER JOIN mobile_user mobile ON mobile.id = identity.mobile_user_id
                WHERE identity.app_id = ?
                  AND identity.openid = ?
                  AND identity.deleted = 0
                  AND mobile.deleted = 0
                LIMIT 1
                """,
                wechatSession.appId(),
                wechatSession.openid()
        );
        if (!existing.isEmpty()) {
            Map<String, Object> identity = existing.getFirst();
            if (!isActive(stringValue(identity.get("identityStatus")))) {
                throw BusinessException.unauthorized("微信身份已停用");
            }
            Long mobileUserId = longValue(identity.get("mobileUserId"));
            jdbcTemplate.update(
                    """
                    UPDATE wechat_identity
                    SET unionid = ?, session_key_hash = ?, updated_by = ?
                    WHERE app_id = ? AND openid = ? AND deleted = 0
                    """,
                    wechatSession.unionid(),
                    wechatSession.sessionKeyHash(),
                    0L,
                    wechatSession.appId(),
                    wechatSession.openid()
            );
            return mobileUserId;
        }

        String mobileUserCode = JdbcMaps.code("MPU");
        jdbcTemplate.update(
                """
                INSERT INTO mobile_user
                  (code, nickname, user_type, status, last_login_at, created_by, updated_by, remark)
                VALUES (?, ?, 'PUBLIC', 'ENABLED', NOW(), ?, ?, ?)
                """,
                mobileUserCode,
                "微信用户",
                0L,
                0L,
                "微信登录自动创建"
        );
        Long mobileUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM mobile_user WHERE code = ? AND deleted = 0 LIMIT 1",
                Long.class,
                mobileUserCode
        );
        jdbcTemplate.update(
                """
                INSERT INTO wechat_identity
                  (mobile_user_id, app_id, openid, unionid, session_key_hash,
                   status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, 'ENABLED', ?, ?, ?)
                """,
                mobileUserId,
                wechatSession.appId(),
                wechatSession.openid(),
                wechatSession.unionid(),
                wechatSession.sessionKeyHash(),
                0L,
                0L,
                "微信小程序登录"
        );
        return mobileUserId;
    }

    private Map<String, Object> sessionFor(Long mobileUserId) {
        return sessionFor(mobileUserId, null, null);
    }

    private Map<String, Object> sessionFor(
            Long mobileUserId,
            String explicitBindingState,
            List<Map<String, Object>> explicitIdentities) {
        AuthPrincipal principal = MobileApiSupport.currentPrincipal(jdbcTemplate, mobileUserId)
                .orElseThrow(() -> BusinessException.unauthorized("移动端登录状态无效"));
        return sessionFor(principal, explicitBindingState, explicitIdentities);
    }

    private Map<String, Object> publicSessionFor(Long mobileUserId) {
        AuthPrincipal principal = new AuthPrincipal(
                mobileUserId,
                "mobile:" + mobileUserId,
                "PUBLIC",
                null,
                List.of("MOBILE_USER"),
                MobileApiSupport.capabilities("PUBLIC")
        );
        return sessionFor(principal, null, List.of());
    }

    private Map<String, Object> sessionFor(
            AuthPrincipal principal,
            String explicitBindingState,
            List<Map<String, Object>> explicitIdentities) {
        Map<String, Object> me = meFor(principal, explicitBindingState, explicitIdentities);
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("token", tokenService.createToken(principal));
        session.put("identity", MobileApiSupport.normalizeUserType(principal.userType()));
        session.putAll(me);
        return session;
    }

    private Map<String, Object> meFor(
            AuthPrincipal principal,
            String explicitBindingState,
            List<Map<String, Object>> explicitIdentities) {
        List<Map<String, Object>> identities = explicitIdentities == null
                ? mobileIdentityService.availableIdentities(principal.userId())
                : explicitIdentities;
        String userType = MobileApiSupport.normalizeUserType(principal.userType());
        String bindingState = explicitBindingState == null
                ? mobileIdentityService.bindingState(principal.userId(), userType)
                : explicitBindingState;
        return MobileApiSupport.me(jdbcTemplate, principal, identities, bindingState);
    }

    private boolean isActive(String status) {
        return "ACTIVE".equals(status) || "ENABLED".equals(status);
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

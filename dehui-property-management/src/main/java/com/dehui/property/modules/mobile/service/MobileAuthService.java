package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileAuthResponse;
import com.dehui.property.modules.mobile.dto.MobileBindInternalRequest;
import com.dehui.property.modules.mobile.dto.MobileBindTenantRequest;
import com.dehui.property.modules.mobile.dto.MobileDevLoginRequest;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import com.dehui.property.modules.mobile.entity.TenantContact;
import com.dehui.property.modules.mobile.entity.WechatUser;
import com.dehui.property.modules.mobile.repository.TenantContactRepository;
import com.dehui.property.modules.mobile.repository.WechatUserRepository;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.repository.SysUserRepository;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.modules.tenant.service.TenantContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MobileAuthService {

    private final WechatUserRepository wechatUserRepository;
    private final TenantContactRepository tenantContactRepository;
    private final SysUserRepository sysUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantContactService tenantContactService;

    private final ConcurrentHashMap<String, Long> tokenStore = new ConcurrentHashMap<>();

    @Transactional
    public MobileAuthResponse devLogin(MobileDevLoginRequest request) {
        String openId = normalizeOpenId(request);
        WechatUser user = wechatUserRepository.findByOpenId(openId)
                .orElseGet(() -> {
                    WechatUser created = new WechatUser();
                    created.setOpenId(openId);
                    created.setStatus("ACTIVE");
                    created.setUserType("PUBLIC");
                    return created;
                });

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone());
        }
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        WechatUser saved = wechatUserRepository.save(user);
        return issueToken(saved);
    }

    public WechatUser getByToken(String token) {
        Long userId = tokenStore.get(token);
        if (userId == null) {
            return null;
        }
        return wechatUserRepository.findById(userId).orElse(null);
    }

    public MobileUserProfile getProfile(String token) {
        WechatUser user = getByToken(token);
        if (user == null) {
            return null;
        }
        return toProfile(user);
    }

    @Transactional
    public Result<MobileAuthResponse> bindInternal(String token, MobileBindInternalRequest request) {
        WechatUser wechatUser = getByToken(token);
        if (wechatUser == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        SysUser sysUser = sysUserRepository.findByUsername(request.getUsername()).orElse(null);
        if (sysUser == null || !sysUser.getPassword().equals(request.getPassword())) {
            return Result.error("账号或密码错误");
        }
        if (!"ACTIVE".equals(sysUser.getStatus())) {
            return Result.error("内部账号已停用");
        }

        wechatUser.setUserType("INTERNAL");
        wechatUser.setBoundSysUserId(sysUser.getId());
        wechatUser.setBoundTenantId(null);
        WechatUser saved = wechatUserRepository.save(wechatUser);
        return Result.success(new MobileAuthResponse(token, toProfile(saved)));
    }

    @Transactional
    public Result<MobileAuthResponse> bindTenant(String token, MobileBindTenantRequest request) {
        WechatUser wechatUser = getByToken(token);
        if (wechatUser == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        TenantContact contact = resolveTenantContact(request);
        if (contact == null) {
            return Result.error("租户联系人不存在，或手机号/密码错误");
        }
        if (!"ACTIVE".equals(contact.getStatus())) {
            return Result.error("租户联系人已停用，请联系物业");
        }

        wechatUser.setPhone(contact.getPhone());
        wechatUser.setNickname(contact.getName());
        wechatUser.setUserType("TENANT");
        wechatUser.setBoundTenantId(contact.getTenantId());
        wechatUser.setBoundSysUserId(null);

        contact.setLastBindTime(LocalDateTime.now());
        contact.setLastLoginAt(LocalDateTime.now());
        tenantContactRepository.save(contact);

        WechatUser saved = wechatUserRepository.save(wechatUser);
        return Result.success(new MobileAuthResponse(token, toProfile(saved)));
    }

    private TenantContact resolveTenantContact(MobileBindTenantRequest request) {
        if (Boolean.TRUE.equals(request.getDevMode())) {
            return resolveDevTenantContact(request);
        }

        TenantContact contact = tenantContactRepository.findByPhone(request.getPhone()).orElse(null);
        if (contact == null || !tenantContactService.matchesPassword(contact, request.getPassword())) {
            return null;
        }
        return contact;
    }

    private TenantContact resolveDevTenantContact(MobileBindTenantRequest request) {
        if (request.getTenantId() == null) {
            return null;
        }
        Tenant tenant = tenantRepository.findById(request.getTenantId()).orElse(null);
        if (tenant == null) {
            return null;
        }

        TenantContact contact = tenantContactRepository.findByPhone(request.getPhone())
                .orElseGet(TenantContact::new);
        contact.setTenantId(request.getTenantId());
        contact.setName(isBlank(request.getName()) ? tenant.getTenantName() : request.getName().trim());
        contact.setPhone(request.getPhone().trim());
        contact.setRole(isBlank(request.getRole()) ? "开发联系人" : request.getRole().trim());
        contact.setIsPrimary(contact.getIsPrimary() != null && contact.getIsPrimary());
        contact.setStatus("ACTIVE");
        return tenantContactRepository.save(contact);
    }

    private MobileAuthResponse issueToken(WechatUser user) {
        String token = "MOBILE-" + UUID.randomUUID();
        tokenStore.put(token, user.getId());
        return new MobileAuthResponse(token, toProfile(user));
    }

    private String normalizeOpenId(MobileDevLoginRequest request) {
        if (request.getDevOpenId() != null && !request.getDevOpenId().isBlank()) {
            return request.getDevOpenId();
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            return "DEV-" + request.getPhone();
        }
        return "DEV-" + UUID.randomUUID();
    }

    private MobileUserProfile toProfile(WechatUser user) {
        MobileUserProfile profile = new MobileUserProfile();
        profile.setId(user.getId());
        profile.setPhone(user.getPhone());
        profile.setNickname(user.getNickname());
        profile.setAvatar(user.getAvatar());
        profile.setUserType(user.getUserType());
        profile.setBoundSysUserId(user.getBoundSysUserId());
        profile.setBoundTenantId(user.getBoundTenantId());
        profile.setStatus(user.getStatus());

        if (user.getBoundSysUserId() != null) {
            sysUserRepository.findById(user.getBoundSysUserId()).ifPresent(sysUser -> {
                profile.setBoundSysUsername(sysUser.getUsername());
                profile.setBoundSysRealName(sysUser.getRealName());
            });
        }

        if (user.getBoundTenantId() != null) {
            tenantRepository.findById(user.getBoundTenantId())
                    .ifPresent(tenant -> profile.setBoundTenantName(tenant.getTenantName()));
        }

        return profile;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

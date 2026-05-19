package com.dehui.property.modules.mobile.repository;

import com.dehui.property.modules.mobile.entity.WechatUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface WechatUserRepository extends JpaRepository<WechatUser, Long> {
    Optional<WechatUser> findByOpenId(String openId);

    Optional<WechatUser> findByPhone(String phone);

    List<WechatUser> findByBoundTenantId(Long boundTenantId);

    Optional<WechatUser> findByBoundTenantIdAndPhone(Long boundTenantId, String phone);
}

package com.dehui.property.modules.mobile.repository;

import com.dehui.property.modules.mobile.entity.WechatUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WechatUserRepository extends JpaRepository<WechatUser, Long> {
    Optional<WechatUser> findByOpenId(String openId);

    Optional<WechatUser> findByPhone(String phone);
}

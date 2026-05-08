package com.dehui.property.modules.mobile.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "wechat_user")
@EqualsAndHashCode(callSuper = true)
public class WechatUser extends BaseEntity {

    private String openId;

    private String unionId;

    private String phone;

    private String nickname;

    private String avatar;

    private String userType;

    private Long boundSysUserId;

    private Long boundTenantId;

    private String status;
}

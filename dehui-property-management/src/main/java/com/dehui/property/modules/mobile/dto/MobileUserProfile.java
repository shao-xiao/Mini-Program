package com.dehui.property.modules.mobile.dto;

import lombok.Data;

@Data
public class MobileUserProfile {
    private Long id;
    private String phone;
    private String nickname;
    private String avatar;
    private String userType;
    private Long boundSysUserId;
    private String boundSysUsername;
    private String boundSysRealName;
    private Long boundTenantId;
    private String boundTenantName;
    private String status;
}

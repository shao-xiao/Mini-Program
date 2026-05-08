package com.dehui.property.modules.mobile.dto;

import lombok.Data;

@Data
public class MobileDevLoginRequest {
    private String devOpenId;
    private String phone;
    private String nickname;
}

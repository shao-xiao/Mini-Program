package com.dehui.property.modules.mobile.dto;

import jakarta.validation.constraints.NotBlank;

public record WechatLoginRequest(
        @NotBlank String code,
        String phoneCode,
        Boolean restoreBoundIdentity
) {
    public WechatLoginRequest(String code, String phoneCode) {
        this(code, phoneCode, null);
    }

    public boolean shouldRestoreBoundIdentity() {
        return !Boolean.FALSE.equals(restoreBoundIdentity);
    }
}

package com.dehui.property.modules.mobile.dto;

import jakarta.validation.constraints.NotBlank;

public record WechatLoginRequest(
        @NotBlank String code,
        String phoneCode
) {
}

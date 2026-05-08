package com.dehui.property.modules.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MobileAuthResponse {
    private String token;
    private MobileUserProfile profile;
}

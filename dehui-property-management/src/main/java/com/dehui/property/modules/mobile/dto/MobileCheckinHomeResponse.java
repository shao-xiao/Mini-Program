package com.dehui.property.modules.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MobileCheckinHomeResponse {
    private MobileUserProfile profile;
    private List<MobileCheckinResponse> checkins;
}

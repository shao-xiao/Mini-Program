package com.dehui.property.modules.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MobileBillListResponse {
    private MobileUserProfile profile;
    private MobileBillSummaryResponse summary;
    private List<MobileBillResponse> bills;
}

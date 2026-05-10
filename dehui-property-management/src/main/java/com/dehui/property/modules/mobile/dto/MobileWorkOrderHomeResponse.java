package com.dehui.property.modules.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MobileWorkOrderHomeResponse {
    private MobileUserProfile profile;
    private List<MobileWorkOrderResponse> workOrders;
}

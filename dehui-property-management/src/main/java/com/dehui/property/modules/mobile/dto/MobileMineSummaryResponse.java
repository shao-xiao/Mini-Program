package com.dehui.property.modules.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMineSummaryResponse {
    private long announcementCount;
    private long availableRoomCount;
    private Long billCount;
    private Long meetingCount;
    private Long workOrderCount;
    private Long visitorCount;
    private boolean needTenantBind;
}

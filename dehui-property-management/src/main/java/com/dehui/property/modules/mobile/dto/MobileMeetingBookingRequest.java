package com.dehui.property.modules.mobile.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MobileMeetingBookingRequest {
    @NotNull(message = "会议室不能为空")
    private Long meetingRoomId;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private String purpose;

    private String departmentName;

    private String contactPhone;

    private String billingMode;
}

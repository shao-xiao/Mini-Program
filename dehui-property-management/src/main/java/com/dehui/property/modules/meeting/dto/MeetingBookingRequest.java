package com.dehui.property.modules.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MeetingBookingRequest {
    @NotNull(message = "会议室不能为空")
    private Long meetingRoomId;

    @NotBlank(message = "申请人类型不能为空")
    private String applicantType;

    private Long tenantId;

    private Long internalUserId;

    private String applicantName;

    private String departmentName;

    private String contactPhone;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private String purpose;

    @NotBlank(message = "计费方式不能为空")
    private String billingMode;

    private BigDecimal discountRate;
}

package com.dehui.property.modules.meeting.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MeetingBookingRequest {
    @NotNull(message = "会议室不能为空")
    private Long roomId;

    private Long meetingRoomId;

    private String sourceType;

    private String applicantType;

    private Long tenantId;

    private String tenantName;

    private Long internalUserId;

    private String applicantName;

    private String department;

    private String departmentName;

    private String applicantPhone;

    private String contactPhone;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private String remark;

    private String purpose;

    private String feeType;

    private String billingMode;

    private BigDecimal discountRate;
}

package com.dehui.property.modules.meeting.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MeetingBookingResponse {
    private Long id;
    private String bookingNumber;
    private Long meetingRoomId;
    private String meetingRoomName;
    private String applicantType;
    private Long tenantId;
    private Long internalUserId;
    private String applicantName;
    private String departmentName;
    private String contactPhone;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String purpose;
    private String billingMode;
    private BigDecimal discountRate;
    private BigDecimal calculatedAmount;
    private Long billId;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

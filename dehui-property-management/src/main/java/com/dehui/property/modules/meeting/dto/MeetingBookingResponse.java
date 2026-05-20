package com.dehui.property.modules.meeting.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MeetingBookingResponse {
    private Long id;
    private String bookingNo;
    private String bookingNumber;
    private Long roomId;
    private Long meetingRoomId;
    private String roomName;
    private String meetingRoomName;
    private String sourceType;
    private String sourceTypeText;
    private String applicantType;
    private Long tenantId;
    private String tenantName;
    private Long internalUserId;
    private String applicantName;
    private String department;
    private String departmentName;
    private String applicantPhone;
    private String contactPhone;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
    private String purpose;
    private String feeType;
    private String feeTypeText;
    private String billingMode;
    private BigDecimal discountRate;
    private BigDecimal amount;
    private BigDecimal calculatedAmount;
    private Long billingId;
    private Long billId;
    private String billStatus;
    private Boolean billPaid;
    private String status;
    private String statusText;
    private LocalDateTime confirmTime;
    private LocalDateTime cancelTime;
    private LocalDateTime completeTime;
    private String cancelReason;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

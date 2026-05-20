package com.dehui.property.modules.meeting.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meeting_booking")
@EqualsAndHashCode(callSuper = true)
public class MeetingBooking extends BaseEntity {

    private String bookingNumber;

    private String bookingNo;

    private Long meetingRoomId;

    private Long roomId;

    private String roomName;

    private String applicantType;

    private String sourceType;

    private Long tenantId;

    private String tenantName;

    private Long internalUserId;

    private String applicantName;

    private String departmentName;

    private String department;

    private String contactPhone;

    private String applicantPhone;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String purpose;

    private String billingMode;

    private String feeType;

    private BigDecimal discountRate;

    private BigDecimal calculatedAmount;

    private BigDecimal amount;

    private Long billId;

    private Long billingId;

    private String status;

    private LocalDateTime confirmTime;

    private LocalDateTime cancelTime;

    private LocalDateTime completeTime;

    private String cancelReason;

    private String remark;

    private String createdBy;

    private String updatedBy;
}

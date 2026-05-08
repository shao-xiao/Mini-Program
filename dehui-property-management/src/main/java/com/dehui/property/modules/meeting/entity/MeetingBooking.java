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

    private Long meetingRoomId;

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
}

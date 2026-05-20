package com.dehui.property.modules.meeting.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "meeting_booking_log")
@EqualsAndHashCode(callSuper = true)
public class MeetingBookingLog extends BaseEntity {
    private Long bookingId;
    private String action;
    private String oldStatus;
    private String newStatus;
    private String operator;
    private String remark;
}

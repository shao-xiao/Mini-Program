package com.dehui.property.modules.meeting.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MeetingBookingLogResponse {
    private Long id;
    private Long bookingId;
    private String action;
    private String actionText;
    private String oldStatus;
    private String newStatus;
    private String operator;
    private String remark;
    private LocalDateTime createdTime;
}

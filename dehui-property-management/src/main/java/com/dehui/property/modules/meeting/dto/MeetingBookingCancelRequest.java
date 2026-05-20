package com.dehui.property.modules.meeting.dto;

import lombok.Data;

@Data
public class MeetingBookingCancelRequest {
    private String cancelReason;
    private String operator;
}

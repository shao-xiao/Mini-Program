package com.dehui.property.modules.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class MeetingBookingCalculateResponse {
    private String feeType;
    private BigDecimal amount;
    private long minutes;
    private String rateDescription;
}

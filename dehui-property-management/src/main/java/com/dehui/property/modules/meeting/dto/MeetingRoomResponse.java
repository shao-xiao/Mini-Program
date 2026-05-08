package com.dehui.property.modules.meeting.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MeetingRoomResponse {
    private Long id;
    private String roomName;
    private String location;
    private Integer capacity;
    private String facilities;
    private BigDecimal workdayWorkHourRate;
    private BigDecimal workdayOffHourRate;
    private BigDecimal holidayRate;
    private String status;
    private Boolean available;
    private String unavailableReason;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
